package model

import (
	"time"

	"gorm.io/plugin/soft_delete"
)

// BaseModel 对应 Spring Boot BaseEntity + MetaObjectHandlerConfig。
//
// 列名与 Spring Boot 表结构严格对齐：
//   - create_time / update_time（不是 GORM 默认的 created_at / updated_at）
//   - deleted TINYINT（不是 GORM 默认的 deleted_at DATETIME）
//     通过 gorm.io/plugin/soft_delete 实现整数标志位软删除（0=正常, 1=已删除）
type BaseModel struct {
	ID        int64                 `gorm:"primaryKey;autoIncrement"            json:"id"`
	CreatedAt time.Time             `gorm:"column:create_time;autoCreateTime"   json:"createTime"`
	UpdatedAt time.Time             `gorm:"column:update_time;autoUpdateTime"   json:"updateTime"`
	// deleted: 0=未删除, 1=已删除；soft_delete.FlagDeleted 令 GORM 在查询时自动追加 WHERE deleted=0
	Deleted   soft_delete.DeletedAt `gorm:"softDelete:flag;column:deleted"      json:"-"`
}

// UserOwnedModel 在 BaseModel 之上增加归属字段，供用户私有资源表使用。
// KB/文档/提示词/工具/MCP/Agent/Workflow 均嵌入此结构体。
type UserOwnedModel struct {
	BaseModel
	CreateBy int64 `gorm:"column:create_by;index" json:"createBy"`
}
