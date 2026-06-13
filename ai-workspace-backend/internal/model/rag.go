package model

// RagSession 知识库问答会话，对应 rag_session 表。
// 与 ChatSession 同构但独立成表：RAG 会话额外绑定一个知识库（KbID），
// 归属同样按 UserID 隔离，防止越权访问他人问答记录（IDOR）。
type RagSession struct {
	BaseModel
	// 归属用户 ID；list/get/delete 均按此列过滤
	UserID int64 `gorm:"column:user_id;index" json:"userId"`
	// 绑定的知识库 ID；问答检索固定在该知识库内
	KbID  int64  `gorm:"column:kb_id;index"   json:"kbId"`
	Title string `gorm:"column:title;size:200" json:"title"`
	// 会话绑定的模型名称，新建时由前端选定，问答时随请求透传给 FastAPI
	ModelName string `gorm:"column:model_name;size:100" json:"modelName"`
}

func (RagSession) TableName() string { return "rag_session" }

// RagMessage 单条知识库问答消息，对应 rag_message 表。
// 归属通过 SessionID 间接关联到用户，无独立用户 ID 列。
type RagMessage struct {
	BaseModel
	SessionID int64 `gorm:"column:session_id;index" json:"sessionId"`
	// role: "user" | "assistant"
	Role    string `gorm:"column:role;size:20"      json:"role"`
	Content string `gorm:"column:content;type:text" json:"content"`
	// Sources 仅 assistant 消息有值：引用来源的 JSON 数组（[]RagSource 序列化），
	// 持久化后重开会话即可还原来源引用卡片；前端按 JSON 字符串反序列化展示。
	Sources string `gorm:"column:sources;type:text" json:"sources"`
	// LLM 本次回复消耗的 token 数，用于成本统计
	TokenCount int `gorm:"column:token_count;default:0" json:"tokenCount"`
}

func (RagMessage) TableName() string { return "rag_message" }