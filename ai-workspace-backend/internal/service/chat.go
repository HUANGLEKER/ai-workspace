package service

import (
	"errors"
	"strings"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
)

// ChatSvc 是聊天服务单例，由 service.Init() 在基础设施就绪后组装
var ChatSvc *ChatService

// maxContextMessages 发送给 LLM 的最近上下文消息条数上限，防止 token 成本随会话长度无限增长
const maxContextMessages = 20

// ChatService 聊天业务逻辑；依赖经构造函数注入，便于单测以 sqlite 内存库构造
type ChatService struct {
	db *gorm.DB
}

// NewChatService 构造聊天服务
func NewChatService(db *gorm.DB) *ChatService {
	return &ChatService{db: db}
}

// ListSessions 查询当前用户的所有会话，按创建时间倒序
func (s *ChatService) ListSessions(userID int64) ([]model.ChatSession, error) {
	var sessions []model.ChatSession
	err := s.db.Scopes(ownedScope[model.ChatSession](userID)).Order("create_time DESC").Find(&sessions).Error
	return sessions, err
}

// CreateSession 新建会话，强制 UserID 为当前用户，防止伪造归属
func (s *ChatService) CreateSession(sess *model.ChatSession, userID int64) error {
	sess.ID = 0
	sess.UserID = userID
	if sess.Title == "" {
		sess.Title = "新会话"
	}
	return s.db.Create(sess).Error
}

// DeleteSession 删除会话前先校验归属，同时级联软删除该会话下所有消息
func (s *ChatService) DeleteSession(id, userID int64) error {
	if err := s.getOwned(id, userID); err != nil {
		return err
	}
	// 事务保证会话与其消息的删除原子性
	return s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("session_id = ?", id).Delete(&model.ChatMessage{}).Error; err != nil {
			return err
		}
		return tx.Delete(&model.ChatSession{}, id).Error
	})
}

// defaultSessionTitles 视为「未命名」的标题集合，命中则首条消息可自动生成标题
var defaultSessionTitles = map[string]bool{"": true, "新对话": true, "新会话": true}

// AutoTitleFromFirstMessage 若会话仍是默认标题，用首条用户消息内容生成标题。
// title 取内容首行并截断到 30 个字符；非默认标题（用户已手动命名）则不覆盖。
// 返回最终标题（未变更时返回原标题），便于上层透传给前端。
func (s *ChatService) AutoTitleFromFirstMessage(sess *model.ChatSession, content string) string {
	if !defaultSessionTitles[sess.Title] {
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
	if err := s.db.Model(&model.ChatSession{}).Where("id = ?", sess.ID).Update("title", title).Error; err != nil {
		return sess.Title
	}
	sess.Title = title
	return title
}

// RenameSession 重命名会话，先校验归属再更新标题（防 IDOR）
func (s *ChatService) RenameSession(id, userID int64, title string) error {
	if err := s.getOwned(id, userID); err != nil {
		return err
	}
	return s.db.Model(&model.ChatSession{}).Where("id = ?", id).Update("title", title).Error
}

// GetOwnedSession 校验会话归属，用于消息读写前的权限检查（防 IDOR）
func (s *ChatService) GetOwnedSession(id, userID int64) (*model.ChatSession, error) {
	return getOwnedResource[model.ChatSession](s.db, id, userID, "会话")
}

func (s *ChatService) getOwned(id, userID int64) error {
	_, err := s.GetOwnedSession(id, userID)
	return err
}

// ListMessages 查询会话下所有消息，按时间升序（对话历史展示用）
func (s *ChatService) ListMessages(sessionID int64) ([]model.ChatMessage, error) {
	var messages []model.ChatMessage
	// 按 id 排序：create_time 秒级精度下同秒消息会乱序
	err := s.db.Where("session_id = ?", sessionID).Order("id ASC").Find(&messages).Error
	return messages, err
}

// ListRecentMessages 查询最近 N 条消息，用于构建有界 LLM 上下文
// 按时间倒序取 N 条后再正序返回，保证传给 LLM 的消息时序正确
func (s *ChatService) ListRecentMessages(sessionID int64) ([]model.ChatMessage, error) {
	var messages []model.ChatMessage
	err := s.db.Where("session_id = ?", sessionID).
		Order("id DESC").
		Limit(maxContextMessages).
		Find(&messages).Error
	if err != nil {
		return nil, err
	}
	for i, j := 0, len(messages)-1; i < j; i, j = i+1, j-1 {
		messages[i], messages[j] = messages[j], messages[i]
	}
	return messages, nil
}

// SaveMessage 持久化一条消息，role 为 "user" 或 "assistant"
func (s *ChatService) SaveMessage(sessionID int64, role, content string) error {
	msg := &model.ChatMessage{
		SessionID: sessionID,
		Role:      role,
		Content:   content,
	}
	return s.db.Create(msg).Error
}

// ListModels 查询所有启用的 LLM 模型配置，ApiKey 字段不返回（json:"-"）
func (s *ChatService) ListModels() ([]model.ChatModel, error) {
	var models []model.ChatModel
	err := s.db.Where("enabled = 1").Find(&models).Error
	return models, err
}

// GetModelConfigByName 按模型名查启用的模型配置，用于多模型路由：
// 把 chat_model 表配置的 api_url/api_key 随请求透传给 FastAPI（llm_config），
// 不同会话即可走不同提供方。未配置或未启用时返回 nil（FastAPI 回退 .env 默认配置）。
func (s *ChatService) GetModelConfigByName(modelName string) *model.ChatModel {
	if modelName == "" {
		return nil
	}
	var m model.ChatModel
	if err := s.db.Where("model_name = ? AND enabled = 1", modelName).First(&m).Error; err != nil {
		return nil
	}
	return &m
}

// LlmConfigBody 把模型配置转为 FastAPI llm_config 字段；api_url/api_key 均为空时
// 返回 nil（没有可覆盖项，让 FastAPI 直接走默认配置）。密钥仅在服务间内网流转。
func LlmConfigBody(m *model.ChatModel) map[string]string {
	if m == nil || (m.ApiUrl == "" && m.ApiKey == "") {
		return nil
	}
	return map[string]string{"api_base": m.ApiUrl, "api_key": m.ApiKey}
}

// PageModels 分页查询全部模型配置（含禁用），供管理员模型管理页使用
func (s *ChatService) PageModels(pageNum, pageSize int, modelName string) (common.PageResult[model.ChatModel], error) {
	var models []model.ChatModel
	var total int64
	q := s.db.Model(&model.ChatModel{})
	if modelName != "" {
		q = q.Where("model_name LIKE ?", "%"+modelName+"%")
	}
	if err := q.Count(&total).Error; err != nil {
		return common.PageResult[model.ChatModel]{}, err
	}
	pg := common.PageQuery{PageNum: pageNum, PageSize: pageSize}
	pg.Normalize()
	if err := q.Offset(pg.Offset()).Limit(pg.PageSize).Order("id DESC").Find(&models).Error; err != nil {
		return common.PageResult[model.ChatModel]{}, err
	}
	return common.PageResult[model.ChatModel]{Total: total, PageNum: pg.PageNum, PageSize: pg.PageSize, List: models}, nil
}

// UpdateModelStatus 启用/禁用模型
func (s *ChatService) UpdateModelStatus(id int64, enabled int8) error {
	return s.db.Model(&model.ChatModel{}).Where("id = ?", id).Update("enabled", enabled).Error
}

// AddModel 新增 LLM 模型配置，仅管理员可操作
func (s *ChatService) AddModel(m *model.ChatModel) error {
	m.ID = 0
	return s.db.Create(m).Error
}

// UpdateModel 更新 LLM 模型配置
func (s *ChatService) UpdateModel(m *model.ChatModel) error {
	if m.ID == 0 {
		return common.NewBizError(common.CodeBadRequest, "模型ID不能为空")
	}
	var existing model.ChatModel
	if err := s.db.First(&existing, m.ID).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.ErrNotFound("模型")
		}
		return err
	}
	// ApiKey 的 json tag 为 "-"，请求体不会带入；Save 全字段覆盖前必须回填，否则密钥被清空
	if m.ApiKey == "" {
		m.ApiKey = existing.ApiKey
	}
	m.CreatedAt = existing.CreatedAt
	return s.db.Save(m).Error
}

// DeleteModel 删除 LLM 模型配置
func (s *ChatService) DeleteModel(id int64) error {
	return s.db.Delete(&model.ChatModel{}, id).Error
}
