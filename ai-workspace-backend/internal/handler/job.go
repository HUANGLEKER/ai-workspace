package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/scheduler"
	"github.com/aiworkspace/backend/internal/service"
)

// ListJobs GET /api/job/page — 仅管理员，分页查询定时任务
func ListJobs(c *gin.Context) {
	pg := common.ParsePage(c)
	jobName := c.Query("jobName")
	result, err := service.JobSvc.PageJobs(pg.PageNum, pg.PageSize, jobName)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, result)
}

// ListJobHandlers GET /api/job/handlers — 返回所有已注册 handler 名称，供前端下拉选择
func ListJobHandlers(c *gin.Context) {
	common.OK(c, scheduler.Registry.Names())
}

// AddJob POST /api/job/add — 新增任务，校验 cron 表达式与 handler 存在性
func AddJob(c *gin.Context) {
	var job model.SysJob
	if err := c.ShouldBindJSON(&job); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.JobSvc.AddJob(&job); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, job)
}

// UpdateJob PUT /api/job/update — 更新任务并重新调度
func UpdateJob(c *gin.Context) {
	var job model.SysJob
	if err := c.ShouldBindJSON(&job); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.JobSvc.UpdateJob(&job); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteJob DELETE /api/job/delete/:id — 移除调度后物理删除
func DeleteJob(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.JobSvc.DeleteJob(id); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// UpdateJobStatus PUT /api/job/status — 切换任务暂停/运行状态
func UpdateJobStatus(c *gin.Context) {
	var req struct {
		ID     int64 `json:"id"     binding:"required"`
		Status int8  `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.JobSvc.ChangeStatus(req.ID, req.Status); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "状态更新成功")
}

// RunJob POST /api/job/run/:id — 立即触发一次执行（异步），不影响 cron 节奏
func RunJob(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.JobSvc.RunOnce(id); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "已触发执行")
}

// ListJobLogs GET /api/job/log/page — 分页查询执行日志
func ListJobLogs(c *gin.Context) {
	pg := common.ParsePage(c)
	var jobID int64
	if v := c.Query("jobId"); v != "" {
		parseID64(v, &jobID)
	}
	result, err := service.JobSvc.PageJobLogs(pg.PageNum, pg.PageSize, jobID)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, result)
}

// CleanJobLogs DELETE /api/job/log/clean — 物理删除所有执行日志
func CleanJobLogs(c *gin.Context) {
	if err := service.JobSvc.CleanLogs(); err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OKMsg(c, "日志已清空")
}

func parseID64(s string, dst *int64) {
	var v int64
	for _, ch := range s {
		if ch < '0' || ch > '9' {
			return
		}
		v = v*10 + int64(ch-'0')
	}
	*dst = v
}
