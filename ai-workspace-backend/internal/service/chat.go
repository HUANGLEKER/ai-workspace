package service

import (
	"errors"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
)

// ChatSvc 是聊天服务全局单例
var ChatSvc = &chatService{}

// maxContextMessages 发送给 LLM 的最近上下文消息条数上限，防止 token 成本随会话长度无限增长
const maxContextMessages = 20

type chatService struct{}

// ListSessions 查询当前用户的所有会话，按创建时间倒序
func (s *chatService) ListSessions(userID int64) ([]model.ChatSession, error) {
	var sessions []model.ChatSession
	err := database.DB.Where("user_id = ?", userID).Order("create_time DESC").Find(&sessions).Error
	return sessions, err
}

// CreateSession 新建会话，强制 UserID 为当前用户，防止伪造归属
func (s *chatService) CreateSession(sess *model.ChatSession, userID int64) error {
	sess.ID = 0
	sess.UserID = userID
	if sess.Title == "" {
		sess.Title = "新会话"
	}
	return database.DB.Create(sess).Error
}

// DeleteSession 删除会话前先校验归属，同时级联软删除该会话下所有消息
func (s *chatService) DeleteSession(id, userID int64) error {
	if err := s.getOwned(id, userID); err != nil {
		return err
	}
	// 事务保证会话与其消息的删除原子性
	return database.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("session_id = ?", id).Delete(&model.ChatMessage{}).Error; err != nil {
			return err
		}
		return tx.Delete(&model.ChatSession{}, id).Error
	})
}

// GetOwnedSession 校验会话归属，用于消息读写前的权限检查（防 IDOR）
func (s *chatService) GetOwnedSession(id, userID int64) (*model.ChatSession, error) {
	var sess model.ChatSession
	err := database.DB.First(&sess, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound("会话")
	}
	if err != nil {
		return nil, err
	}
	if sess.UserID != userID {
		return nil, common.ErrForbidden()
	}
	return &sess, nil
}

func (s *chatService) getOwned(id, userID int64) error {
	_, err := s.GetOwnedSession(id, userID)
	return err
}

// ListMessages 查询会话下所有消息，按时间升序（对话历史展示用）
func (s *chatService) ListMessages(sessionID int64) ([]model.ChatMessage, error) {
	var messages []model.ChatMessage
	// 按 id 排序：create_time 秒级精度下同秒消息会乱序
	err := database.DB.Where("session_id = ?", sessionID).Order("id ASC").Find(&messages).Error
	return messages, err
}

// ListRecentMessages 查询最近 N 条消息，用于构建有界 LLM 上下文
// 按时间倒序取 N 条后再正序返回，保证传给 LLM 的消息时序正确
func (s *chatService) ListRecentMessages(sessionID int64) ([]model.ChatMessage, error) {
	var messages []model.ChatMessage
	err := database.DB.Where("session_id = ?", sessionID).
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
func (s *chatService) SaveMessage(sessionID int64, role, content string) error {
	msg := &model.ChatMessage{
		SessionID: sessionID,
		Role:      role,
		Content:   content,
	}
	return database.DB.Create(msg).Error
}

// ListModels 查询所有启用的 LLM 模型配置，ApiKey 字段不返回（json:"-"）
func (s *chatService) ListModels() ([]model.ChatModel, error) {
	var models []model.ChatModel
	err := database.DB.Where("enabled = 1").Find(&models).Error
	return models, err
}

// PageModels 分页查询全部模型配置（含禁用），供管理员模型管理页使用
func (s *chatService) PageModels(pageNum, pageSize int, modelName string) (common.PageResult[model.ChatModel], error) {
	var models []model.ChatModel
	var total int64
	q := database.DB.Model(&model.ChatModel{})
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
func (s *chatService) UpdateModelStatus(id int64, enabled int8) error {
	return database.DB.Model(&model.ChatModel{}).Where("id = ?", id).Update("enabled", enabled).Error
}

// AddModel 新增 LLM 模型配置，仅管理员可操作
func (s *chatService) AddModel(m *model.ChatModel) error {
	m.ID = 0
	return database.DB.Create(m).Error
}

// UpdateModel 更新 LLM 模型配置
func (s *chatService) UpdateModel(m *model.ChatModel) error {
	if m.ID == 0 {
		return common.NewBizError(common.CodeBadRequest, "模型ID不能为空")
	}
	var existing model.ChatModel
	if err := database.DB.First(&existing, m.ID).Error; err != nil {
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
	return database.DB.Save(m).Error
}

// DeleteModel 删除 LLM 模型配置
func (s *chatService) DeleteModel(id int64) error {
	return database.DB.Delete(&model.ChatModel{}, id).Error
}
