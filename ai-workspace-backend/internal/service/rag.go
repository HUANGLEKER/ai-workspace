package service

import (
	"strings"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/model"
)

// RagSvc 是知识库问答服务单例，由 service.Init() 组装
var RagSvc *RagService

// ragContextMessages 发送给 LLM 的最近上下文消息条数上限（多轮追问用），
// 控制 token 成本；RAG 不做滚动摘要，仅保留最近窗口。
const ragContextMessages = 10

// RagService 知识库问答的会话/消息持久化逻辑。
// 与 ChatService 同构但独立成表，归属按 user_id 隔离（防 IDOR）。
type RagService struct {
	db *gorm.DB
}

// NewRagService 构造知识库问答服务
func NewRagService(db *gorm.DB) *RagService {
	return &RagService{db: db}
}

// ListSessions 查询当前用户的问答会话；kbID>0 时仅返回该知识库下的会话，按创建时间倒序
func (s *RagService) ListSessions(userID, kbID int64) ([]model.RagSession, error) {
	var sessions []model.RagSession
	q := s.db.Scopes(ownedScope[model.RagSession](userID))
	if kbID > 0 {
		q = q.Where("kb_id = ?", kbID)
	}
	err := q.Order("create_time DESC").Find(&sessions).Error
	return sessions, err
}

// CreateSession 新建会话，强制 UserID 为当前用户，防止伪造归属
func (s *RagService) CreateSession(sess *model.RagSession, userID int64) error {
	sess.ID = 0
	sess.UserID = userID
	if sess.Title == "" {
		sess.Title = "新问答"
	}
	return s.db.Create(sess).Error
}

// GetOwnedSession 校验会话归属，用于消息读写前的权限检查（防 IDOR）
func (s *RagService) GetOwnedSession(id, userID int64) (*model.RagSession, error) {
	return getOwnedResource[model.RagSession](s.db, id, userID, "问答会话")
}

func (s *RagService) getOwned(id, userID int64) error {
	_, err := s.GetOwnedSession(id, userID)
	return err
}

// RenameSession 重命名会话，先校验归属再更新标题（防 IDOR）
func (s *RagService) RenameSession(id, userID int64, title string) error {
	if err := s.getOwned(id, userID); err != nil {
		return err
	}
	return s.db.Model(&model.RagSession{}).Where("id = ?", id).Update("title", title).Error
}

// DeleteSession 删除会话前先校验归属，同时级联软删除该会话下所有消息
func (s *RagService) DeleteSession(id, userID int64) error {
	if err := s.getOwned(id, userID); err != nil {
		return err
	}
	return s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("session_id = ?", id).Delete(&model.RagMessage{}).Error; err != nil {
			return err
		}
		return tx.Delete(&model.RagSession{}, id).Error
	})
}

// ClearMessages 清空会话下的全部消息（保留会话本身），先校验归属
func (s *RagService) ClearMessages(id, userID int64) error {
	if err := s.getOwned(id, userID); err != nil {
		return err
	}
	return s.db.Where("session_id = ?", id).Delete(&model.RagMessage{}).Error
}

// ListMessages 查询会话下所有消息，按 id 升序（含 assistant 消息的 sources JSON）
func (s *RagService) ListMessages(sessionID int64) ([]model.RagMessage, error) {
	var messages []model.RagMessage
	err := s.db.Where("session_id = ?", sessionID).Order("id ASC").Find(&messages).Error
	return messages, err
}

// RecentContextMessages 取最近 N 条消息，按 id 倒序取后再正序返回，供多轮追问的上下文
func (s *RagService) RecentContextMessages(sessionID int64) ([]model.RagMessage, error) {
	var messages []model.RagMessage
	err := s.db.Where("session_id = ?", sessionID).
		Order("id DESC").
		Limit(ragContextMessages).
		Find(&messages).Error
	if err != nil {
		return nil, err
	}
	for i, j := 0, len(messages)-1; i < j; i, j = i+1, j-1 {
		messages[i], messages[j] = messages[j], messages[i]
	}
	return messages, nil
}

// ragDefaultTitles 视为「未命名」的标题集合，命中则首条消息可自动生成标题
var ragDefaultTitles = map[string]bool{"": true, "新问答": true, "新对话": true}

// AutoTitleFromFirstMessage 若会话仍是默认标题，用首条用户问题生成标题（截断 30 字）。
// 返回最终标题（未变更时返回原标题），便于上层透传给前端实时更新侧边栏。
func (s *RagService) AutoTitleFromFirstMessage(sess *model.RagSession, content string) string {
	if !ragDefaultTitles[sess.Title] {
		return sess.Title
	}
	title := strings.TrimSpace(content)
	if idx := strings.IndexByte(title, '\n'); idx >= 0 {
		title = strings.TrimSpace(title[:idx])
	}
	r := []rune(title)
	if len(r) > 30 {
		title = string(r[:30]) + "…"
	}
	if title == "" {
		return sess.Title
	}
	if err := s.db.Model(&model.RagSession{}).Where("id = ?", sess.ID).Update("title", title).Error; err != nil {
		return sess.Title
	}
	sess.Title = title
	return title
}

// SaveMessage 持久化一条消息（user/assistant）
func (s *RagService) SaveMessage(sessionID int64, role, content string) error {
	return s.db.Create(&model.RagMessage{SessionID: sessionID, Role: role, Content: content}).Error
}

// SaveAssistantMessage 持久化 assistant 回复，连同引用来源 JSON 与 token 消耗一并落库（断连保存）
func (s *RagService) SaveAssistantMessage(sessionID int64, content, sources string, tokenCount int) error {
	return s.db.Create(&model.RagMessage{
		SessionID:  sessionID,
		Role:       "assistant",
		Content:    content,
		Sources:    sources,
		TokenCount: tokenCount,
	}).Error
}
