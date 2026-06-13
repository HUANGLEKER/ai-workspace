package service

import (
	"testing"
	"time"

	"github.com/aiworkspace/backend/internal/model"
)

func newUsageEnv(t *testing.T) (*UsageService, *ChatService) {
	db := newTestDB(t)
	if err := db.AutoMigrate(&model.UsageDaily{}); err != nil {
		t.Fatalf("迁移 usage_daily 失败: %v", err)
	}
	return NewUsageService(db), NewChatService(db)
}

func seedMsgs(t *testing.T, cs *ChatService, userID int64, modelName string, tokens []int) {
	t.Helper()
	sess := &model.ChatSession{Title: "t", ModelName: modelName}
	if err := cs.CreateSession(sess, userID); err != nil {
		t.Fatalf("建会话失败: %v", err)
	}
	for _, tk := range tokens {
		_ = cs.SaveMessage(sess.ID, "user", "q") // user 消息不计入聚合
		if err := cs.SaveMessageWithTokens(sess.ID, "assistant", "a", tk); err != nil {
			t.Fatalf("存消息失败: %v", err)
		}
	}
}

// 聚合正确性：按用户+模型分组求和，user 消息不计入；重复执行幂等
func TestAggregateDayIdempotent(t *testing.T) {
	us, cs := newUsageEnv(t)
	seedMsgs(t, cs, userA, "m1", []int{10, 20})
	seedMsgs(t, cs, userA, "m2", []int{5})
	seedMsgs(t, cs, userB, "m1", []int{7})

	for i := 0; i < 2; i++ { // 跑两遍验证幂等（先删后插）
		if err := us.AggregateDay(time.Now()); err != nil {
			t.Fatalf("聚合失败: %v", err)
		}
	}

	var rows []model.UsageDaily
	us.db.Order("user_id, model_name").Find(&rows)
	if len(rows) != 3 {
		t.Fatalf("期望 3 行聚合，实际 %d: %+v", len(rows), rows)
	}
	check := func(i int, uid int64, m string, tokens, cnt int64) {
		r := rows[i]
		if r.UserID != uid || r.ModelName != m || r.Tokens != tokens || r.MsgCount != cnt {
			t.Fatalf("行 %d 不符: %+v", i, r)
		}
	}
	check(0, userA, "m1", 30, 2)
	check(1, userA, "m2", 5, 1)
	check(2, userB, "m1", 7, 1)
}

// 空模型名归入 (default)
func TestAggregateDefaultModel(t *testing.T) {
	us, cs := newUsageEnv(t)
	seedMsgs(t, cs, userA, "", []int{3})
	if err := us.AggregateDay(time.Now()); err != nil {
		t.Fatalf("聚合失败: %v", err)
	}
	var row model.UsageDaily
	us.db.First(&row)
	if row.ModelName != "(default)" || row.Tokens != 3 {
		t.Fatalf("默认模型行不符: %+v", row)
	}
}

// UserTotals：历史走聚合表、今日实时，二者相加且按用户隔离
func TestUserTotals(t *testing.T) {
	us, cs := newUsageEnv(t)
	// 历史聚合行（昨天）
	yesterday := time.Now().AddDate(0, 0, -1).Format("2006-01-02")
	us.db.Create(&model.UsageDaily{UserID: userA, StatDate: yesterday, ModelName: "m1", Tokens: 100, MsgCount: 2})
	us.db.Create(&model.UsageDaily{UserID: userB, StatDate: yesterday, ModelName: "m1", Tokens: 999, MsgCount: 1})
	// 今日实时消息
	seedMsgs(t, cs, userA, "m1", []int{40})

	total, today := us.UserTotals(userA)
	if today != 40 || total != 140 {
		t.Fatalf("期望 total=140 today=40，实际 total=%d today=%d", total, today)
	}
}
