package service

import (
	"context"

	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/repository"
)

// AgentSvc 是 Agent 服务全局单例
var AgentSvc *AgentService

// AgentService 业务逻辑层；持久化经 repository 完成（P0 分层重构），ai 经构造函数注入
type AgentService struct {
	repo repository.OwnedRepository[model.Agent]
	ai   RunCaller
}

// NewAgentService 构造服务
func NewAgentService(db *gorm.DB, ai RunCaller) *AgentService {
	return &AgentService{repo: repository.NewOwnedRepository[model.Agent](db), ai: ai}
}

// ListByUser 查询当前用户拥有的所有 Agent
func (s *AgentService) ListByUser(userID int64) ([]model.Agent, error) {
	return s.repo.List(userID, func(q *gorm.DB) *gorm.DB { return q.Order("create_time DESC") })
}

// GetByID 按 ID 查询 Agent（不校验归属，用于展示）
func (s *AgentService) GetByID(id int64) (*model.Agent, error) {
	return s.repo.FindByID(id, "Agent")
}

// Create 新建 Agent，强制 CreateBy 为当前用户
func (s *AgentService) Create(a *model.Agent, userID int64) error {
	if a.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "Agent 名称不能为空")
	}
	a.ID = 0
	a.CreateBy = userID
	if a.Enabled == 0 {
		a.Enabled = 1
	}
	return s.repo.Create(a)
}

// Update 更新 Agent，回填 CreateBy 防止归属被篡改
func (s *AgentService) Update(a *model.Agent, userID int64) error {
	existing, err := s.GetOwned(a.ID, userID)
	if err != nil {
		return err
	}
	a.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	a.CreatedAt = existing.CreatedAt
	return s.repo.Save(a)
}

// Delete 校验归属后删除 Agent
func (s *AgentService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return s.repo.DeleteByID(id)
}

// GetOwned 校验 Agent 归属，防止 IDOR
func (s *AgentService) GetOwned(id, userID int64) (*model.Agent, error) {
	return s.repo.FindOwned(id, userID, "Agent")
}

// BuildRunBody 校验归属、解析工具与 MCP 服务器，组装发给 FastAPI 的请求体。
// 一次性运行（Run）与流式运行（handler 直连 FastAPI Stream）共用此方法，保证规格一致。
func (s *AgentService) BuildRunBody(agentID, userID int64, input, sessionID string, isAdmin bool) (map[string]any, error) {
	agent, err := s.GetOwned(agentID, userID)
	if err != nil {
		return nil, err
	}

	tools, err := ToolSvc.ResolveForAgent(userID, agent.Tools)
	if err != nil {
		return nil, err
	}

	mcpServers, err := MCPSvc.ResolveForAgent(userID, agent.McpServers, isAdmin)
	if err != nil {
		return nil, err
	}

	// FastAPI AgentRunRequest 的 session_id 为必填字符串
	if sessionID == "" {
		sessionID = "default"
	}

	return map[string]any{
		"agent_id":      agentID,
		"input":         input,
		"session_id":    sessionID,
		"model":         agent.Model,
		"system_prompt": agent.SystemPrompt,
		"tools":         tools,
		"mcp_servers":   mcpServers,
	}, nil
}

// Run 解析 Agent 引用的工具与 MCP 服务器，组装完整规格后 POST 给 FastAPI 执行工具调用循环
func (s *AgentService) Run(ctx context.Context, agentID, userID int64, input, sessionID string, isAdmin bool) (any, error) {
	body, err := s.BuildRunBody(agentID, userID, input, sessionID, isAdmin)
	if err != nil {
		return nil, err
	}
	data, err := s.ai.PostForData(ctx, "/agent/run", body)
	if err != nil {
		return nil, err
	}
	return data, nil
}
