package model

// ChatSession 聊天会话，对应 chat_session 表。
// 一个用户可拥有多个会话，按 UserID 隔离，防止越权访问他人对话（IDOR）。
type ChatSession struct {
	BaseModel
	// 归属用户 ID；list/get/delete 均按此列过滤，不同于 KB 的 createBy 列名
	UserID    int64  `gorm:"column:user_id;index"      json:"userId"`
	Title     string `gorm:"column:title;size:200"     json:"title"`
	// 会话绑定的模型名称，由前端从 chat_model 列表选定后传入，FastAPI 不读 MySQL
	ModelName string `gorm:"column:model_name;size:100" json:"modelName"`
}

func (ChatSession) TableName() string { return "chat_session" }

// ChatMessage 单条聊天消息，对应 chat_message 表。
// 归属通过 SessionID 间接关联到用户，无独立用户 ID 列。
type ChatMessage struct {
	BaseModel
	SessionID  int64  `gorm:"column:session_id;index"   json:"sessionId"`
	// role: "user" | "assistant"
	Role       string `gorm:"column:role;size:20"        json:"role"`
	Content    string `gorm:"column:content;type:text"   json:"content"`
	// LLM 本次回复消耗的 token 数，用于成本统计
	TokenCount int    `gorm:"column:token_count;default:0" json:"tokenCount"`
}

func (ChatMessage) TableName() string { return "chat_message" }

// ChatModel LLM 模型配置，对应 chat_model 表。
// 完全由 Spring Boot 侧管理；FastAPI 不读此表，模型名随请求体透传。
type ChatModel struct {
	BaseModel
	ModelName string `gorm:"column:model_name;size:100" json:"modelName"`
	Provider  string `gorm:"column:provider;size:50"    json:"provider"`
	ApiUrl    string `gorm:"column:api_url;size:500"    json:"apiUrl"`
	// ApiKey 存储敏感凭证，响应中不返回此字段
	ApiKey    string `gorm:"column:api_key;size:500"    json:"-"`
	Enabled   int8   `gorm:"column:enabled;default:1"   json:"enabled"`
}

func (ChatModel) TableName() string { return "chat_model" }
