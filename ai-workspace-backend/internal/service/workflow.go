package service

import (
	"context"
	"errors"
	"strconv"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
	"github.com/aiworkspace/backend/pkg/fastapi"
)

// WorkflowSvc 是工作流服务全局单例
var WorkflowSvc = &workflowService{}

type workflowService struct{}

// ListByUser 查询当前用户拥有的所有工作流
func (s *workflowService) ListByUser(userID int64) ([]model.Workflow, error) {
	var workflows []model.Workflow
	err := database.DB.Where("create_by = ?", userID).Order("create_time DESC").Find(&workflows).Error
	return workflows, err
}

// GetByID 按 ID 查询工作流
func (s *workflowService) GetByID(id int64) (*model.Workflow, error) {
	var w model.Workflow
	if err := database.DB.First(&w, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.ErrNotFound("工作流")
		}
		return nil, err
	}
	return &w, nil
}

// Create 新建工作流，强制 CreateBy 为当前用户
func (s *workflowService) Create(w *model.Workflow, userID int64) error {
	if w.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "工作流名称不能为空")
	}
	w.ID = 0
	w.CreateBy = userID
	if w.Enabled == 0 {
		w.Enabled = 1
	}
	return database.DB.Create(w).Error
}

// Update 更新工作流，回填 CreateBy 防止归属被篡改
func (s *workflowService) Update(w *model.Workflow, userID int64) error {
	existing, err := s.GetOwned(w.ID, userID)
	if err != nil {
		return err
	}
	w.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	w.CreatedAt = existing.CreatedAt
	return database.DB.Save(w).Error
}

// Delete 校验归属后删除工作流
func (s *workflowService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return database.DB.Delete(&model.Workflow{}, id).Error
}

// GetOwned 校验工作流归属，防止 IDOR
func (s *workflowService) GetOwned(id, userID int64) (*model.Workflow, error) {
	var w model.Workflow
	err := database.DB.First(&w, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound("工作流")
	}
	if err != nil {
		return nil, err
	}
	if w.CreateBy != userID {
		return nil, common.ErrForbidden()
	}
	return &w, nil
}

// Run 执行工作流：校验归属后将 definition 与输入 POST 给 FastAPI /workflow/run
func (s *workflowService) Run(ctx context.Context, workflowID, userID int64, input string) (any, error) {
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
	data, err := fastapi.Client.PostForData(ctx, "/workflow/run", body)
	if err != nil {
		return nil, err
	}
	return data, nil
}
