package model

// Owned 由用户私有资源模型实现，声明自己的归属列与归属人。
// 各模块归属列名历史上不一致（KB/Agent 等用 create_by，Chat 会话用 user_id，
// 文件用 upload_by），曾因混用列名出过 IDOR 漏洞。统一经此接口声明后，
// service 层一律通过 owned.go 的泛型 helper 做归属过滤/校验，
// 不允许再手写 Where("create_by = ?") 之类的字面量条件。
type Owned interface {
	// OwnerColumn 返回归属列名，用于列表过滤
	OwnerColumn() string
	// OwnerID 返回该记录的归属用户 ID，用于单条记录的归属校验
	OwnerID() int64
}

// UserOwnedModel：KB/文档/提示词/工具/MCP/Agent/Workflow 等嵌入它的模型统一获得 create_by 归属
func (m UserOwnedModel) OwnerColumn() string { return "create_by" }
func (m UserOwnedModel) OwnerID() int64      { return m.CreateBy }

// ChatSession 的归属列是 user_id（区别于 UserOwnedModel 的 create_by）
func (m ChatSession) OwnerColumn() string { return "user_id" }
func (m ChatSession) OwnerID() int64      { return m.UserID }

// FileInfo 的归属列是 upload_by（区别于 KB 的 create_by）
func (m FileInfo) OwnerColumn() string { return "upload_by" }
func (m FileInfo) OwnerID() int64      { return m.UploadBy }
