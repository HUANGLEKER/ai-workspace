package model

// Workflow 用户私有工作流定义，对应 workflow 表。
// Definition 字段存储 LangGraph 可解析的 JSON 图结构，由 FastAPI /workflow/run 执行。
type Workflow struct {
	UserOwnedModel
	Name        string `gorm:"column:name;size:100"          json:"name"`
	Description string `gorm:"column:description;size:500"   json:"description"`
	// LangGraph 工作流图定义 JSON，节点与边均在此字段中描述
	Definition string `gorm:"column:definition;type:text"   json:"definition"`
	Model      string `gorm:"column:model;size:100"         json:"model"`
	Enabled    int8   `gorm:"column:enabled;default:1"      json:"enabled"`
}

func (Workflow) TableName() string { return "workflow" }
