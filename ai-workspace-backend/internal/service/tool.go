package service

import (
	"encoding/json"
	"errors"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
)

// ToolSvc 是工具服务全局单例
var ToolSvc = &toolService{}

type toolService struct{}

// ListByUser 查询当前用户注册的所有工具
func (s *toolService) ListByUser(userID int64) ([]model.Tool, error) {
	var tools []model.Tool
	err := database.DB.Where("create_by = ?", userID).Order("create_time DESC").Find(&tools).Error
	return tools, err
}

// GetOwned 查询并校验工具归属，防止 IDOR
func (s *toolService) GetOwned(id, userID int64) (*model.Tool, error) {
	var t model.Tool
	err := database.DB.First(&t, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound("工具")
	}
	if err != nil {
		return nil, err
	}
	if t.CreateBy != userID {
		return nil, common.ErrForbidden()
	}
	return &t, nil
}

// Create 新建工具，强制 CreateBy 为当前用户
func (s *toolService) Create(t *model.Tool, userID int64) error {
	if t.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "工具名称不能为空")
	}
	t.ID = 0
	t.CreateBy = userID
	if t.Enabled == 0 {
		t.Enabled = 1
	}
	return database.DB.Create(t).Error
}

// Update 更新工具，回填 CreateBy 防止归属被篡改
func (s *toolService) Update(t *model.Tool, userID int64) error {
	existing, err := s.GetOwned(t.ID, userID)
	if err != nil {
		return err
	}
	t.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	t.CreatedAt = existing.CreatedAt
	return database.DB.Save(t).Error
}

// Delete 校验归属后删除工具
func (s *toolService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return database.DB.Delete(&model.Tool{}, id).Error
}

// ResolveForAgent 按工具名列表（JSON 数组字符串）解析用户名下已启用的完整工具规格
// 返回结构供 FastAPI /agent/run 使用。仅加载 tool_type=http 的工具（builtin 由 FastAPI 自注册）
func (s *toolService) ResolveForAgent(userID int64, toolNamesJSON string) ([]map[string]any, error) {
	var names []string
	if toolNamesJSON != "" && toolNamesJSON != "[]" {
		if err := json.Unmarshal([]byte(toolNamesJSON), &names); err != nil {
			return nil, err
		}
	}
	if len(names) == 0 {
		return []map[string]any{}, nil
	}

	var tools []model.Tool
	err := database.DB.Where("create_by = ? AND name IN ? AND enabled = 1", userID, names).Find(&tools).Error
	if err != nil {
		return nil, err
	}

	result := make([]map[string]any, 0, len(tools))
	for _, t := range tools {
		// FastAPI HttpToolSpec.config 是字典；DB 中存的是 JSON 字符串，须先解析
		config := map[string]any{}
		if t.Config != "" {
			_ = json.Unmarshal([]byte(t.Config), &config)
		}
		spec := map[string]any{
			"name":        t.Name,
			"description": t.Description,
			"type":        t.ToolType,
			"endpoint":    t.Endpoint,
			"config":      config,
		}
		result = append(result, spec)
	}
	return result, nil
}
