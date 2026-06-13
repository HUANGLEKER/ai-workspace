package service

import (
	"context"
	"strconv"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/repository"
)

// WorkflowSvc 是工作流服务全局单例
var WorkflowSvc *WorkflowService

// WorkflowService 业务逻辑层；持久化经 repository 完成（P0 分层重构），ai 经构造函数注入
type WorkflowService struct {
	repo repository.OwnedRepository[model.Workflow]
	ai   RunCaller
}

// NewWorkflowService 构造服务
func NewWorkflowService(db *gorm.DB, ai RunCaller) *WorkflowService {
	return &WorkflowService{repo: repository.NewOwnedRepository[model.Workflow](db), ai: ai}
}

// ListByUser 查询当前用户拥有的所有工作流
func (s *WorkflowService) ListByUser(userID int64) ([]model.Workflow, error) {
	return s.repo.List(userID, func(q *gorm.DB) *gorm.DB { return q.Order("create_time DESC") })
}

// GetByID 按 ID 查询工作流
func (s *WorkflowService) GetByID(id int64) (*model.Workflow, error) {
	return s.repo.FindByID(id, "工作流")
}

// Create 新建工作流，强制 CreateBy 为当前用户
func (s *WorkflowService) Create(w *model.Workflow, userID int64) error {
	if w.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "工作流名称不能为空")
	}
	w.ID = 0
	w.CreateBy = userID
	if w.Enabled == 0 {
		w.Enabled = 1
	}
	return s.repo.Create(w)
}

// Update 更新工作流，回填 CreateBy 防止归属被篡改
func (s *WorkflowService) Update(w *model.Workflow, userID int64) error {
	existing, err := s.GetOwned(w.ID, userID)
	if err != nil {
		return err
	}
	w.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	w.CreatedAt = existing.CreatedAt
	return s.repo.Save(w)
}

// Delete 校验归属后删除工作流
func (s *WorkflowService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return s.repo.DeleteByID(id)
}

// GetOwned 校验工作流归属，防止 IDOR
func (s *WorkflowService) GetOwned(id, userID int64) (*model.Workflow, error) {
	return s.repo.FindOwned(id, userID, "工作流")
}

// Run 执行工作流：校验归属后将 definition 与输入 POST 给 FastAPI /workflow/run
func (s *WorkflowService) Run(ctx context.Context, workflowID, userID int64, input string) (any, error) {
	wf, err := s.GetOwned(workflowID, userID)
	if err != nil {
		return nil, err
	}
	// 契约对齐 FastAPI WorkflowRunRequest：workflow_id 为字符串，
	// session_id 必填，输入为 inputs 字典（默认图从 inputs.prompt 取提示词）
	body := map[string]any{
		"workflow_id": strconv.FormatInt(workflowID, 10),
		"session_id":  "default",
		"inputs":      map[string]any{"prompt": input},
		"model":       wf.Model,
		"definition":  wf.Definition,
	}
	data, err := s.ai.PostForData(ctx, "/workflow/run", body)
	if err != nil {
		return nil, err
	}
	return data, nil
}
