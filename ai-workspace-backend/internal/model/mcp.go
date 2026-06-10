package model

// McpServer MCP 服务器注册信息，对应 mcp_server 表。
// 用户私有；sse 类型可在 Agent 运行时通过 langchain-mcp-adapters 远程加载工具；
// stdio 类型仅本地可用，Agent 运行时不支持。
type McpServer struct {
	UserOwnedModel
	Name        string `gorm:"column:name;size:100"         json:"name"`
	Description string `gorm:"column:description;size:500"  json:"description"`
	// sse: HTTP SSE 传输；stdio: 标准输入输出（仅本地）
	Transport   string `gorm:"column:transport;size:20"     json:"transport"`
	// SSE 服务器基础 URL，连通性检测与 Agent 运行时均使用此字段
	URL         string `gorm:"column:url;size:500"          json:"url"`
	// stdio 传输的命令行启动指令
	Command     string `gorm:"column:command;size:500"      json:"command"`
	// 扩展配置 JSON：{"headers":{...},"env":{...},"args":[...]}
	Config      string `gorm:"column:config;type:text"      json:"config"`
	// 1=启用，0=禁用
	Enabled     int8   `gorm:"column:enabled;default:1"     json:"enabled"`
}

func (McpServer) TableName() string { return "mcp_server" }
