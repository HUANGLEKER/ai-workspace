package service

import (
	"time"

	"github.com/aiworkspace/backend/internal/model"
	"gorm.io/gorm"
)

// DashboardSvc 是仪表盘统计服务全局单例
var DashboardSvc *DashboardService

// DashboardService 依赖经构造函数注入（P1-1）
type DashboardService struct {
	db *gorm.DB
}

// NewDashboardService 构造服务
func NewDashboardService(db *gorm.DB) *DashboardService {
	return &DashboardService{db: db}
}

// DashboardStats 仪表盘统计视图，所有数字均按用户隔离
type DashboardStats struct {
	TodaySessions int64 `json:"todaySessions"` // 今日新建会话数
	KbCount       int64 `json:"kbCount"`       // 知识库总数
	DocCount      int64 `json:"docCount"`      // 文档总数
	FileCount     int64 `json:"fileCount"`     // 文件总数
}

// GetStats 聚合当前用户的仪表盘统计数据；归属过滤统一走 ownedScope（列名由模型声明）
func (s *DashboardService) GetStats(userID int64) DashboardStats {
	// Truncate(24h) 按 UTC 取整，UTC+8 时区会偏 8 小时；改用本地零点
	now := time.Now()
	startOfToday := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())

	var todaySessions, kbCount, fileCount int64
	s.db.Model(&model.ChatSession{}).
		Scopes(ownedScope[model.ChatSession](userID)).
		Where("create_time >= ?", startOfToday).
		Count(&todaySessions)

	s.db.Model(&model.KbKnowledgeBase{}).
		Scopes(ownedScope[model.KbKnowledgeBase](userID)).
		Count(&kbCount)

	// 文档不直接挂用户，经由用户名下的知识库 ID 集合间接统计
	var kbIDs []int64
	s.db.Model(&model.KbKnowledgeBase{}).
		Scopes(ownedScope[model.KbKnowledgeBase](userID)).
		Pluck("id", &kbIDs)
	var docCount int64
	if len(kbIDs) > 0 {
		s.db.Model(&model.KbDocument{}).Where("kb_id IN ?", kbIDs).Count(&docCount)
	}

	s.db.Model(&model.FileInfo{}).
		Scopes(ownedScope[model.FileInfo](userID)).
		Count(&fileCount)

	return DashboardStats{
		TodaySessions: todaySessions,
		KbCount:       kbCount,
		DocCount:      docCount,
		FileCount:     fileCount,
	}
}
