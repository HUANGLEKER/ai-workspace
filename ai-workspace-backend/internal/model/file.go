package model

// FileInfo 文件中心元数据，对应 file_info 表。
// 注意归属列名为 UploadBy（区别于 KB 的 CreateBy），避免混用导致越权计数或 IDOR。
type FileInfo struct {
	BaseModel
	FileName string `gorm:"column:file_name;size:255"   json:"fileName"`
	// MinIO 对象路径，格式由上传服务定义，presign 时需校验 uploadBy 防止路径枚举攻击
	FilePath string `gorm:"column:file_path;size:500"   json:"filePath"`
	FileSize int64  `gorm:"column:file_size"            json:"fileSize"`
	// MIME 类型，如 image/png、application/pdf
	FileType string `gorm:"column:file_type;size:100"   json:"fileType"`
	// 归属用户 ID，注意此处使用 upload_by 而非 create_by，保持与 Spring Boot 表结构一致
	UploadBy int64 `gorm:"column:upload_by;index"      json:"uploadBy"`
}

func (FileInfo) TableName() string { return "file_info" }
