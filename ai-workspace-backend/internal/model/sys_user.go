package model

type SysUser struct {
	BaseModel
	// 唯一性由 DB 复合索引 uk_username(username, deleted) 保证（见 init.sql），
	// 软删后可重建同名用户；schema 由 SQL 管理，此处不加 uniqueIndex 标签
	Username string `gorm:"column:username;size:64"            json:"username"`
	Password string `gorm:"column:password;size:128"           json:"-"`
	Nickname string `gorm:"column:nickname;size:64"            json:"nickname"`
	Email    string `gorm:"column:email;size:128"              json:"email"`
	Avatar   string `gorm:"column:avatar;size:256"             json:"avatar"`
	Status   int8   `gorm:"column:status;default:1"            json:"status"` // 1=启用 0=禁用
	Remark   string `gorm:"column:remark;size:500"             json:"remark"`
}

func (SysUser) TableName() string { return "sys_user" }

type SysRole struct {
	BaseModel
	RoleName string `gorm:"column:role_name;size:64" json:"roleName"`
	RoleCode string `gorm:"column:role_code;size:64" json:"roleCode"` // 带 ROLE_ 前缀
	Status   int8   `gorm:"column:status;default:1"  json:"status"`
	Remark   string `gorm:"column:remark;size:500"   json:"remark"`
}

func (SysRole) TableName() string { return "sys_role" }

type SysUserRole struct {
	UserID int64 `gorm:"column:user_id;primaryKey" json:"userId"`
	RoleID int64 `gorm:"column:role_id;primaryKey" json:"roleId"`
}

func (SysUserRole) TableName() string { return "sys_user_role" }
