package model

// Tool 工具注册信息，对应 tool 表。
// Agent 运行时按 createBy + name 查找用户名下已启用的工具，解析为 HTTP tool 规格 POST 给 FastAPI。
type Tool struct {
	UserOwnedModel
	// Agent 配置中通过此名称引用工具
	Name        string `gorm:"column:name;size:100"         json:"name"`
	Description string `gorm:"column:description;size:500"  json:"description"`
	// http: 通过 HTTP 调用外部服务；builtin: FastAPI 内置实现
	ToolType string `gorm:"column:tool_type;size:20"     json:"toolType"`
	// HTTP 工具的调用端点，仅 tool_type=http 时有效
	Endpoint string `gorm:"column:endpoint;size:500"     json:"endpoint"`
	// 参数 schema 与调用配置 JSON：{"method":"POST","params":[...],"headers":{...}}
	Config string `gorm:"column:config;type:text"      json:"config"`
	// 1=启用，0=禁用；禁用的工具不会被 Agent 运行时加载
	Enabled int8 `gorm:"column:enabled;default:1"     json:"enabled"`
}

func (Tool) TableName() string { return "tool" }
