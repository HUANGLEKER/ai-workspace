package model

import "time"

// UsageDaily 每日 token 用量聚合，对应 usage_daily 表（P2-2）。
//
// 派生数据：由 usageDailyJob 从 chat_message.token_count 按天重算（先删后插，幂等），
// 因此不走软删除约定（无 deleted 列），与 sys_job_log 同类。
// dashboard 的累计用量查此表（常数时间），当日数据实时补足。
type UsageDaily struct {
	ID     int64 `gorm:"primaryKey;autoIncrement"                              json:"id"`
	UserID int64 `gorm:"column:user_id;uniqueIndex:uk_user_date_model"         json:"userId"`
	// 统计日（本地时区零点对齐），格式 2026-06-13
	StatDate  string    `gorm:"column:stat_date;type:date;uniqueIndex:uk_user_date_model" json:"statDate"`
	ModelName string    `gorm:"column:model_name;size:100;uniqueIndex:uk_user_date_model" json:"modelName"`
	Tokens    int64     `gorm:"column:tokens;default:0"                        json:"tokens"`
	MsgCount  int64     `gorm:"column:msg_count;default:0"                     json:"msgCount"`
	CreatedAt time.Time `gorm:"column:create_time;autoCreateTime"              json:"createTime"`
	UpdatedAt time.Time `gorm:"column:update_time;autoUpdateTime"              json:"updateTime"`
}

func (UsageDaily) TableName() string { return "usage_daily" }
