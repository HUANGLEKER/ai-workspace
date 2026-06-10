package model

// Prompt 提示词模板，对应 prompt 表。
// 用户私有，按 CreateBy 隔离；支持按标题关键字与分类筛选，可复用到 Chat。
type Prompt struct {
	UserOwnedModel
	Title       string `gorm:"column:title;size:200"        json:"title"`
	// 提示词正文，可含占位符供用户填写后使用
	Content     string `gorm:"column:content;type:text"     json:"content"`
	// 自由分类标签，如"写作"、"编程"、"翻译"；可为空
	Category    string `gorm:"column:category;size:100"     json:"category"`
	Description string `gorm:"column:description;size:500"  json:"description"`
}

func (Prompt) TableName() string { return "prompt" }
