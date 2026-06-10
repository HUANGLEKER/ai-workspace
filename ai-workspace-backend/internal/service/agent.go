package service

import (
	"context"
	"errors"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/pkg/database"
	"github.com/aiworkspace/backend/pkg/fastapi"
)

// AgentSvc 是 Agent 服务全局单例
var AgentSvc = &agentService{}

type agentService struct{}

// ListByUser 查询当前用户拥有的所有 Agent
func (s *agentService) ListByUser(userID int64) ([]model.Agent, error) {
	var agents []model.Agent
	err := database.DB.Where("create_by = ?", userID).Order("create_time DESC").Find(&agents).Error
	return agents, err
}

// GetByID 按 ID 查询 Agent（不校验归属，用于展示）
func (s *agentService) GetByID(id int64) (*model.Agent, error) {
	var a model.Agent
	if err := database.DB.First(&a, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.ErrNotFound("Agent")
		}
		return nil, err
	}
	return &a, nil
}

// Create 新建 Agent，强制 CreateBy 为当前用户
func (s *agentService) Create(a *model.Agent, userID int64) error {
	if a.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "Agent 名称不能为空")
	}
	a.ID = 0
	a.CreateBy = userID
	if a.Enabled == 0 {
		a.Enabled = 1
	}
	return database.DB.Create(a).Error
}

// Update 更新 Agent，回填 CreateBy 防止归属被篡改
func (s *agentService) Update(a *model.Agent, userID int64) error {
	existing, err := s.GetOwned(a.ID, userID)
	if err != nil {
		return err
	}
	a.CreateBy = existing.CreateBy
	return database.DB.Save(a).Error
}

// Delete 校验归属后删除 Agent
func (s *agentService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return database.DB.Delete(&model.Agent{}, id).Error
}

// GetOwned 校验 Agent 归属，防止 IDOR
func (s *agentService) GetOwned(id, userID int64) (*model.Agent, error) {
	var a model.Agent
	err := database.DB.First(&a, id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, common.ErrNotFound("Agent")
	}
	if err != nil {
		return nil, err
	}
	if a.CreateBy != userID {
		return nil, common.ErrForbidden()
	}
	return &a, nil
}

// Run 解析 Agent 引用的工具与 MCP 服务器，组装完整规格后 POST 给 FastAPI 执行工具调用循环
func (s *agentService) Run(ctx context.Context, agentID, userID int64, input, sessionID string) (any, error) {
	agent, err := s.GetOwned(agentID, userID)
	if err != nil {
		return nil, err
	}

	tools, err := ToolSvc.ResolveForAgent(userID, agent.Tools)
	if err != nil {
		return nil, err
	}

	mcpServers, err := MCPSvc.ResolveForAgent(userID, agent.McpServers)
	if err != nil {
		return nil, err
	}

	body := map[string]any{
		"agent_id":      agentID,
		"input":         input,
		"session_id":    sessionID,
		"model":         agent.Model,
		"system_prompt": agent.SystemPrompt,
		"tools":         tools,
		"mcp_servers":   mcpServers,
	}

	data, err := fastapi.Client.PostForData(ctx, "/agent/run", body)
	if err != nil {
		return nil, err
	}
	return data, nil
}
