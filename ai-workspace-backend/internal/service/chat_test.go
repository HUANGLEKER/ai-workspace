package service

import (
	"fmt"
	"strings"
	"testing"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

const (
	userA int64 = 1
	userB int64 = 2
)

func newChatSvc(t *testing.T) *ChatService {
	return NewChatService(newTestDB(t))
}

func mustCreateSession(t *testing.T, s *ChatService, userID int64, title string) *model.ChatSession {
	t.Helper()
	sess := &model.ChatSession{Title: title, ModelName: "m"}
	if err := s.CreateSession(sess, userID); err != nil {
		t.Fatalf("创建会话失败: %v", err)
	}
	return sess
}

// 归属校验是 chat 模块的安全核心：他人会话必须 403，不存在的必须 404
func TestSessionOwnership(t *testing.T) {
	s := newChatSvc(t)
	sess := mustCreateSession(t, s, userA, "mine")

	if _, err := s.GetOwnedSession(sess.ID, userA); err != nil {
		t.Fatalf("本人访问应成功: %v", err)
	}
	_, err := s.GetOwnedSession(sess.ID, userB)
	assertBizCode(t, err, common.CodeForbidden)
	_, err = s.GetOwnedSession(99999, userA)
	assertBizCode(t, err, common.CodeNotFound)

	// 越权重命名/删除同样必须被拒绝
	assertBizCode(t, s.RenameSession(sess.ID, userB, "hacked"), common.CodeForbidden)
	assertBizCode(t, s.DeleteSession(sess.ID, userB), common.CodeForbidden)
}

// CreateSession 必须强制归属为当前用户，请求体伪造的 UserID/ID 不得生效
func TestCreateSessionForcesOwnership(t *testing.T) {
	s := newChatSvc(t)
	sess := &model.ChatSession{Title: "t", ModelName: "m"}
	sess.ID = 777
	sess.UserID = userB
	if err := s.CreateSession(sess, userA); err != nil {
		t.Fatalf("创建失败: %v", err)
	}
	if sess.UserID != userA || sess.ID == 777 {
		t.Fatalf("归属未被强制为当前用户: id=%d userID=%d", sess.ID, sess.UserID)
	}
}

// LLM 上下文必须有界（最近 20 条）且时序正确（升序）
func TestListRecentMessagesBoundedAndOrdered(t *testing.T) {
	s := newChatSvc(t)
	sess := mustCreateSession(t, s, userA, "ctx")
	for i := 1; i <= 25; i++ {
		if err := s.SaveMessage(sess.ID, "user", fmt.Sprintf("msg-%d", i)); err != nil {
			t.Fatalf("保存消息失败: %v", err)
		}
	}

	msgs, err := s.ListRecentMessages(sess.ID)
	if err != nil {
		t.Fatalf("查询失败: %v", err)
	}
	if len(msgs) != maxContextMessages {
		t.Fatalf("期望 %d 条，实际 %d", maxContextMessages, len(msgs))
	}
	// 取的是最近 20 条（msg-6..msg-25），且按时序升序
	if msgs[0].Content != "msg-6" || msgs[len(msgs)-1].Content != "msg-25" {
		t.Fatalf("窗口或顺序错误: 首=%s 尾=%s", msgs[0].Content, msgs[len(msgs)-1].Content)
	}
	for i := 1; i < len(msgs); i++ {
		if msgs[i].ID <= msgs[i-1].ID {
			t.Fatalf("消息未按升序返回")
		}
	}
}

// 自动标题：默认标题被首条消息覆盖；手动命名不覆盖；超长截断到 30 字符
func TestAutoTitleFromFirstMessage(t *testing.T) {
	s := newChatSvc(t)

	sess := mustCreateSession(t, s, userA, "新会话")
	got := s.AutoTitleFromFirstMessage(sess, "  第一行标题\n第二行忽略 ")
	if got != "第一行标题" {
		t.Fatalf("自动标题错误: %q", got)
	}

	named := mustCreateSession(t, s, userA, "我手动起的名字")
	if got := s.AutoTitleFromFirstMessage(named, "随便说点什么"); got != "我手动起的名字" {
		t.Fatalf("手动标题不应被覆盖: %q", got)
	}

	long := mustCreateSession(t, s, userA, "新会话")
	got = s.AutoTitleFromFirstMessage(long, strings.Repeat("长", 40))
	if r := []rune(got); len(r) != 31 || !strings.HasSuffix(got, "…") {
		t.Fatalf("超长标题未按 30 字符截断: %q（%d runes）", got, len([]rune(got)))
	}
}

// 删除会话必须级联删除消息，且对 LLM 上下文不可见
func TestDeleteSessionCascadesMessages(t *testing.T) {
	s := newChatSvc(t)
	sess := mustCreateSession(t, s, userA, "bye")
	_ = s.SaveMessage(sess.ID, "user", "hello")
	_ = s.SaveMessage(sess.ID, "assistant", "world")

	if err := s.DeleteSession(sess.ID, userA); err != nil {
		t.Fatalf("删除失败: %v", err)
	}
	msgs, _ := s.ListMessages(sess.ID)
	if len(msgs) != 0 {
		t.Fatalf("消息未级联删除，残留 %d 条", len(msgs))
	}
}

// 多模型路由：启用模型可查到；禁用/不存在/空名返回 nil；LlmConfigBody 空配置返回 nil
func TestGetModelConfigByName(t *testing.T) {
	s := newChatSvc(t)
	_ = s.AddModel(&model.ChatModel{ModelName: "m-on", Provider: "p", ApiUrl: "http://x", ApiKey: "k", Enabled: 1})
	// 注意：Enabled 带 gorm default:1 标签，Create 时零值会被写成 1（GORM 视零值为未设置），
	// 因此「新增即禁用」必须经 UpdateModelStatus 显式落库
	off := &model.ChatModel{ModelName: "m-off", Provider: "p", ApiUrl: "http://y"}
	_ = s.AddModel(off)
	_ = s.UpdateModelStatus(off.ID, 0)

	if got := s.GetModelConfigByName("m-on"); got == nil || got.ApiUrl != "http://x" {
		t.Fatalf("启用模型应可查到: %+v", got)
	}
	if s.GetModelConfigByName("m-off") != nil || s.GetModelConfigByName("nope") != nil || s.GetModelConfigByName("") != nil {
		t.Fatal("禁用/不存在/空名应返回 nil")
	}

	if LlmConfigBody(nil) != nil {
		t.Fatal("nil 模型应返回 nil")
	}
	if LlmConfigBody(&model.ChatModel{ModelName: "bare"}) != nil {
		t.Fatal("url/key 全空应返回 nil（走 FastAPI 默认配置）")
	}
	body := LlmConfigBody(&model.ChatModel{ApiUrl: "http://x", ApiKey: "k"})
	if body["api_base"] != "http://x" || body["api_key"] != "k" {
		t.Fatalf("llm_config 字段映射错误: %v", body)
	}
}

// UpdateModel：请求体不带 apiKey 时必须回填旧密钥，防止 Save 全字段覆盖清空
func TestUpdateModelKeepsApiKey(t *testing.T) {
	s := newChatSvc(t)
	m := &model.ChatModel{ModelName: "m", Provider: "p", ApiKey: "secret", Enabled: 1}
	_ = s.AddModel(m)

	upd := &model.ChatModel{BaseModel: model.BaseModel{ID: m.ID}, ModelName: "m2", Provider: "p", Enabled: 1}
	if err := s.UpdateModel(upd); err != nil {
		t.Fatalf("更新失败: %v", err)
	}
	if got := s.GetModelConfigByName("m2"); got == nil || got.ApiKey != "secret" {
		t.Fatalf("apiKey 被清空: %+v", got)
	}
}
