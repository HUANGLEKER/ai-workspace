package model

// KbKnowledgeBase 知识库，对应 kb_knowledge_base 表。
// 用户私有资源，按 CreateBy 隔离，作为文档的逻辑容器。
type KbKnowledgeBase struct {
	UserOwnedModel
	KbName      string `gorm:"column:kb_name;size:100"        json:"kbName"`
	Description string `gorm:"column:description;size:500"    json:"description"`
}

func (KbKnowledgeBase) TableName() string { return "kb_knowledge_base" }

// KbDocument 知识库文档元数据，对应 kb_document 表。
// 文件实体存储于 MinIO，路径格式：kb/{kbId}/{uuid}.{ext}；
// 向量数据存储于 ChromaDB，通过 FastAPI /embedding 端点管理。
type KbDocument struct {
	BaseModel
	KbID     int64  `gorm:"column:kb_id;index"           json:"kbId"`
	FileName string `gorm:"column:file_name;size:255"    json:"fileName"`
	// MinIO 对象路径，格式：kb/{kbId}/{uuid}.{ext}
	FilePath string `gorm:"column:file_path;size:500"    json:"filePath"`
	FileSize int64  `gorm:"column:file_size"             json:"fileSize"`
	FileType string `gorm:"column:file_type;size:50"     json:"fileType"`
	// 嵌入状态机：PENDING → PROCESSING → DONE / FAILED
	Status   string `gorm:"column:status;size:20;default:PENDING" json:"status"`
}

func (KbDocument) TableName() string { return "kb_document" }

// KbChunkTask 异步嵌入任务审计记录，对应 kb_chunk_task 表。
// 每次触发文档嵌入（含重建）都写入一条记录，失败时保存错误信息用于排查。
type KbChunkTask struct {
	BaseModel
	DocumentID int64  `gorm:"column:document_id;index"      json:"documentId"`
	// 任务状态：PENDING / RUNNING / SUCCESS / FAILED
	TaskStatus string `gorm:"column:task_status;size:20"    json:"taskStatus"`
	ErrorMsg   string `gorm:"column:error_msg;type:text"    json:"errorMsg"`
}

func (KbChunkTask) TableName() string { return "kb_chunk_task" }

// 文档状态常量
const (
	DocStatusPending    = "PENDING"
	DocStatusProcessing = "PROCESSING"
	DocStatusDone       = "DONE"
	DocStatusFailed     = "FAILED"
)

// 任务状态常量
const (
	TaskStatusPending = "PENDING"
	TaskStatusRunning = "RUNNING"
	TaskStatusSuccess = "SUCCESS"
	TaskStatusFailed  = "FAILED"
)
