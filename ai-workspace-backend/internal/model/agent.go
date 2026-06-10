package model

// Agent 用户私有 Agent 定义，对应 agent 表。
// 运行时由 Spring Boot 解析 Tools/McpServers 引用，拼装完整规格后 POST 给 FastAPI 执行工具调用循环。
type Agent struct {
	UserOwnedModel
	Name         string `gorm:"column:name;size:100"             json:"name"`
	Description  string `gorm:"column:description;size:500"      json:"description"`
	// 系统提示词，注入 Agent 的人格与行为约束
	SystemPrompt string `gorm:"column:system_prompt;type:text"   json:"systemPrompt"`
	// 本次对话使用的 LLM 模型名，透传给 FastAPI
	Model        string `gorm:"column:model;size:100"            json:"model"`
	// 工具名称 JSON 数组字符串，如 ["search","calculator"]；
	// 运行时按 createBy + name 查找用户名下启用的工具，解析为 HTTP tool 规格
	Tools        string `gorm:"column:tools;type:text"           json:"tools"`
	// MCP 服务器名称 JSON 数组字符串；运行时解析为 SSE endpoint，由 langchain-mcp-adapters 加载
	McpServers   string `gorm:"column:mcp_servers;type:text"     json:"mcpServers"`
	Enabled      int8   `gorm:"column:enabled;default:1"         json:"enabled"`
}

func (Agent) TableName() string { return "agent" }
