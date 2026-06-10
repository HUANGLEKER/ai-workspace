package model

import "time"

// SysJob 定时任务定义，对应 sys_job 表。
// 不使用软删除（无 deleted 列），删除时物理移除并取消调度。
// Status: 0=运行中/已调度, 1=暂停
type SysJob struct {
	ID          int64     `gorm:"primaryKey;autoIncrement"           json:"id"`
	JobName     string    `gorm:"column:job_name;size:100"           json:"jobName"`
	JobGroup    string    `gorm:"column:job_group;size:50"           json:"jobGroup"`
	// invokeTarget 对应 Go 侧已注册的 JobHandler bean 名称，创建时校验存在性
	InvokeTarget string   `gorm:"column:invoke_target;size:100"      json:"invokeTarget"`
	// Spring 6 段式 cron（含秒），由 robfig/cron WithSeconds() 解析
	CronExpression string  `gorm:"column:cron_expression;size:100"   json:"cronExpression"`
	JobParams    string    `gorm:"column:job_params;size:500"         json:"jobParams"`
	// 0=运行中，1=暂停
	Status       int8      `gorm:"column:status;default:1"            json:"status"`
	Remark       string    `gorm:"column:remark;size:500"             json:"remark"`
	CreateBy     int64     `gorm:"column:create_by"                   json:"createBy"`
	CreatedAt    time.Time `gorm:"column:create_time;autoCreateTime"  json:"createTime"`
	UpdatedAt    time.Time `gorm:"column:update_time;autoUpdateTime"  json:"updateTime"`
}

func (SysJob) TableName() string { return "sys_job" }

// SysJobLog 任务执行日志，对应 sys_job_log 表。
// 只追加，无软删除列（deleted），无 update_time；"清理日志"为物理删除。
// Status: 0=成功, 1=失败
type SysJobLog struct {
	ID           int64     `gorm:"primaryKey;autoIncrement"           json:"id"`
	JobID        int64     `gorm:"column:job_id;index"                json:"jobId"`
	JobName      string    `gorm:"column:job_name;size:100"           json:"jobName"`
	InvokeTarget string    `gorm:"column:invoke_target;size:100"      json:"invokeTarget"`
	JobParams    string    `gorm:"column:job_params;size:500"         json:"jobParams"`
	// 0=成功，1=失败
	Status       int8      `gorm:"column:status"                      json:"status"`
	JobMessage   string    `gorm:"column:job_message;size:500"        json:"jobMessage"`
	// 异常堆栈（截断至 2000 字符）
	ExceptionInfo string   `gorm:"column:exception_info;type:text"    json:"exceptionInfo"`
	// 执行耗时（毫秒）
	CostMs       int64     `gorm:"column:cost_ms"                     json:"costMs"`
	CreatedAt    time.Time `gorm:"column:create_time;autoCreateTime"  json:"createTime"`
}

func (SysJobLog) TableName() string { return "sys_job_log" }

const (
	JobStatusRunning = int8(0) // 运行中/已调度
	JobStatusPaused  = int8(1) // 暂停

	JobLogStatusSuccess = int8(0)
	JobLogStatusFail    = int8(1)
)
