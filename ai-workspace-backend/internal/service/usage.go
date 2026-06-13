package service

import (
	"fmt"
	"time"

	"go.uber.org/zap"
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/model"
)

// UsageSvc 是用量聚合服务单例，由 service.Init() 组装
var UsageSvc *UsageService

// UsageService 每日 token 用量聚合（P2-2）。
// 数据源是 chat_message.token_count（P2-1 起由 usage 帧落库）；
// 模型名经 chat_session 关联取得（消息行不存模型）。
type UsageService struct {
	db *gorm.DB
}

// NewUsageService 构造用量聚合服务
func NewUsageService(db *gorm.DB) *UsageService {
	return &UsageService{db: db}
}

// dayBounds 返回某天的本地零点区间 [start, end)
func dayBounds(day time.Time) (time.Time, time.Time) {
	start := time.Date(day.Year(), day.Month(), day.Day(), 0, 0, 0, 0, day.Location())
	return start, start.AddDate(0, 0, 1)
}

// dailyRow 聚合查询的中间结果
type dailyRow struct {
	UserID    int64
	ModelName string
	Tokens    int64
	MsgCount  int64
}

// AggregateDay 重算某一天的用量聚合：先删该日旧行再插入（事务内，天然幂等）。
// 不过滤软删除——已删除的会话/消息产生的 token 消耗依然真实发生过。
func (s *UsageService) AggregateDay(day time.Time) error {
	start, end := dayBounds(day)
	date := start.Format("2006-01-02")

	var rows []dailyRow
	err := s.db.Raw(`
		SELECT cs.user_id AS user_id,
		       COALESCE(NULLIF(cs.model_name, ''), '(default)') AS model_name,
		       SUM(cm.token_count) AS tokens,
		       COUNT(*) AS msg_count
		FROM chat_message cm
		JOIN chat_session cs ON cs.id = cm.session_id
		WHERE cm.role = 'assistant'
		  AND cm.create_time >= ? AND cm.create_time < ?
		GROUP BY cs.user_id, COALESCE(NULLIF(cs.model_name, ''), '(default)')`,
		start, end).Scan(&rows).Error
	if err != nil {
		return fmt.Errorf("聚合查询失败: %w", err)
	}

	return s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("stat_date = ?", date).Delete(&model.UsageDaily{}).Error; err != nil {
			return err
		}
		for _, r := range rows {
			rec := &model.UsageDaily{
				UserID:    r.UserID,
				StatDate:  date,
				ModelName: r.ModelName,
				Tokens:    r.Tokens,
				MsgCount:  r.MsgCount,
			}
			if err := tx.Create(rec).Error; err != nil {
				return err
			}
		}
		return nil
	})
}

// AggregateRecent 重算最近 daysBack 天（含今天）的聚合，供定时任务与启动补算调用
func (s *UsageService) AggregateRecent(daysBack int) error {
	now := time.Now()
	for i := daysBack - 1; i >= 0; i-- {
		if err := s.AggregateDay(now.AddDate(0, 0, -i)); err != nil {
			return err
		}
	}
	return nil
}

// UserTotals 当前用户的 token 用量：历史部分查聚合表（常数时间），
// 今天的部分从 chat_message 实时补足（聚合表的今日行可能滞后）。
func (s *UsageService) UserTotals(userID int64) (total int64, today int64) {
	// 历史累计（不含今天，避免与实时部分重复计入）
	todayStr := time.Now().Format("2006-01-02")
	s.db.Model(&model.UsageDaily{}).
		Where("user_id = ? AND stat_date < ?", userID, todayStr).
		Select("COALESCE(SUM(tokens), 0)").Scan(&total)

	start, end := dayBounds(time.Now())
	s.db.Raw(`
		SELECT COALESCE(SUM(cm.token_count), 0)
		FROM chat_message cm
		JOIN chat_session cs ON cs.id = cm.session_id
		WHERE cs.user_id = ? AND cm.role = 'assistant'
		  AND cm.create_time >= ? AND cm.create_time < ?`,
		userID, start, end).Scan(&today)

	return total + today, today
}

// UsageJobHandler 适配 scheduler.JobHandler 接口的每日聚合任务（invoke_target: usageDailyJob）。
// 每次执行重算最近 2 天（跨零点运行时昨日数据完整、今日已有部分也得到刷新）。
type UsageJobHandler struct{}

func (h *UsageJobHandler) Execute(params string) error {
	if UsageSvc == nil {
		return fmt.Errorf("UsageSvc 未初始化")
	}
	if err := UsageSvc.AggregateRecent(2); err != nil {
		return err
	}
	zap.L().Info("用量聚合完成", zap.String("params", params))
	return nil
}
