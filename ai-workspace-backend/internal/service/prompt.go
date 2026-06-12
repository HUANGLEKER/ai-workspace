package service

import (
	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
)

// PromptSvc 是提示词服务全局单例
var PromptSvc = &promptService{}

type promptService struct{}

// ListByUser 查询当前用户的提示词列表，支持标题关键字与分类过滤
func (s *promptService) ListByUser(userID int64, keyword, category string) ([]model.Prompt, error) {
	var prompts []model.Prompt
	q := database.DB.Scopes(ownedScope[model.Prompt](userID))
	if keyword != "" {
		q = q.Where("title LIKE ?", "%"+keyword+"%")
	}
	if category != "" {
		q = q.Where("category = ?", category)
	}
	err := q.Order("create_time DESC").Find(&prompts).Error
	return prompts, err
}

// GetOwned 查询并校验提示词归属，防止 IDOR
func (s *promptService) GetOwned(id, userID int64) (*model.Prompt, error) {
	return getOwnedResource[model.Prompt](database.DB, id, userID, "提示词")
}

// Create 新建提示词，强制 CreateBy 为当前用户
func (s *promptService) Create(p *model.Prompt, userID int64) error {
	if p.Title == "" {
		return common.NewBizError(common.CodeBadRequest, "提示词标题不能为空")
	}
	p.ID = 0
	p.CreateBy = userID
	return database.DB.Create(p).Error
}

// Update 更新提示词，回填 CreateBy 防止归属被篡改
func (s *promptService) Update(p *model.Prompt, userID int64) error {
	existing, err := s.GetOwned(p.ID, userID)
	if err != nil {
		return err
	}
	p.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	p.CreatedAt = existing.CreatedAt
	return database.DB.Save(p).Error
}

// Delete 校验归属后删除提示词
func (s *promptService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return database.DB.Delete(&model.Prompt{}, id).Error
}
