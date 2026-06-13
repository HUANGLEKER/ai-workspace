package service

import (
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/repository"
)

// PromptSvc 是提示词服务全局单例
var PromptSvc *PromptService

// PromptService 业务逻辑层；持久化经 repository 完成（P0 分层重构），不直接触碰 GORM
type PromptService struct {
	repo repository.OwnedRepository[model.Prompt]
}

// NewPromptService 构造服务；签名保持收 *gorm.DB（与 DI 装配/测试一致），内部建仓储
func NewPromptService(db *gorm.DB) *PromptService {
	return &PromptService{repo: repository.NewOwnedRepository[model.Prompt](db)}
}

// ListByUser 查询当前用户的提示词列表，支持标题关键字与分类过滤
func (s *PromptService) ListByUser(userID int64, keyword, category string) ([]model.Prompt, error) {
	return s.repo.List(userID, func(q *gorm.DB) *gorm.DB {
		if keyword != "" {
			q = q.Where("title LIKE ?", "%"+keyword+"%")
		}
		if category != "" {
			q = q.Where("category = ?", category)
		}
		return q.Order("create_time DESC")
	})
}

// GetOwned 查询并校验提示词归属，防止 IDOR
func (s *PromptService) GetOwned(id, userID int64) (*model.Prompt, error) {
	return s.repo.FindOwned(id, userID, "提示词")
}

// Create 新建提示词，强制 CreateBy 为当前用户
func (s *PromptService) Create(p *model.Prompt, userID int64) error {
	if p.Title == "" {
		return common.NewBizError(common.CodeBadRequest, "提示词标题不能为空")
	}
	p.ID = 0
	p.CreateBy = userID
	return s.repo.Create(p)
}

// Update 更新提示词，回填 CreateBy 防止归属被篡改
func (s *PromptService) Update(p *model.Prompt, userID int64) error {
	existing, err := s.GetOwned(p.ID, userID)
	if err != nil {
		return err
	}
	p.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	p.CreatedAt = existing.CreatedAt
	return s.repo.Save(p)
}

// Delete 校验归属后删除提示词
func (s *PromptService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return s.repo.DeleteByID(id)
}
