package service

import (
	"errors"

	"go.uber.org/zap"
	"gorm.io/gorm"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/scheduler"
)

// JobSvc 是定时任务服务全局单例
var JobSvc *JobService

// JobService 依赖经构造函数注入（P1-1）
type JobService struct {
	db *gorm.DB
}

// NewJobService 构造服务
func NewJobService(db *gorm.DB) *JobService {
	return &JobService{db: db}
}

// PageJobs 分页查询任务列表
func (s *JobService) PageJobs(pageNum, pageSize int, jobName string) (common.PageResult[model.SysJob], error) {
	var jobs []model.SysJob
	var total int64
	q := s.db.Model(&model.SysJob{})
	if jobName != "" {
		q = q.Where("job_name LIKE ?", "%"+jobName+"%")
	}
	if err := q.Count(&total).Error; err != nil {
		return common.PageResult[model.SysJob]{}, err
	}
	pg := common.PageQuery{PageNum: pageNum, PageSize: pageSize}
	pg.Normalize()
	if err := q.Offset(pg.Offset()).Limit(pg.PageSize).Order("id DESC").Find(&jobs).Error; err != nil {
		return common.PageResult[model.SysJob]{}, err
	}
	return common.PageResult[model.SysJob]{Total: total, PageNum: pg.PageNum, PageSize: pg.PageSize, List: jobs}, nil
}

// AddJob 新建任务：校验 cron 表达式与 handler 存在性，默认暂停状态，按需加入调度器
func (s *JobService) AddJob(job *model.SysJob) error {
	if err := scheduler.ValidateCron(job.CronExpression); err != nil {
		return common.NewBizError(common.CodeBadRequest, "cron 表达式无效: "+err.Error())
	}
	if _, ok := scheduler.Registry.Get(job.InvokeTarget); !ok {
		return common.NewBizError(common.CodeBadRequest, "handler '"+job.InvokeTarget+"' 未注册")
	}
	job.ID = 0
	if job.Status != model.JobStatusRunning {
		job.Status = model.JobStatusPaused
	}
	if err := s.db.Create(job).Error; err != nil {
		return err
	}
	if job.Status == model.JobStatusRunning {
		return scheduler.Manager.Add(job.ID, job.JobName, job.InvokeTarget, job.CronExpression, job.JobParams)
	}
	return nil
}

// UpdateJob 更新任务：重新校验后，在调度器中先移除再添加（相当于重新调度）
func (s *JobService) UpdateJob(job *model.SysJob) error {
	if err := scheduler.ValidateCron(job.CronExpression); err != nil {
		return common.NewBizError(common.CodeBadRequest, "cron 表达式无效: "+err.Error())
	}
	if _, ok := scheduler.Registry.Get(job.InvokeTarget); !ok {
		return common.NewBizError(common.CodeBadRequest, "handler '"+job.InvokeTarget+"' 未注册")
	}
	existing, err := s.getByID(job.ID)
	if err != nil {
		return err
	}
	scheduler.Manager.Remove(existing.ID)
	// Save 全字段覆盖，回填创建时间与创建人防止被写成零值
	job.CreatedAt = existing.CreatedAt
	job.CreateBy = existing.CreateBy
	if err = s.db.Save(job).Error; err != nil {
		return err
	}
	if job.Status == model.JobStatusRunning {
		return scheduler.Manager.Add(job.ID, job.JobName, job.InvokeTarget, job.CronExpression, job.JobParams)
	}
	return nil
}

// DeleteJob 从调度器移除后物理删除（sys_job 表无软删除列）
func (s *JobService) DeleteJob(id int64) error {
	scheduler.Manager.Remove(id)
	return s.db.Unscoped().Delete(&model.SysJob{}, id).Error
}

// ChangeStatus 切换任务暂停/运行状态，并同步调度器
func (s *JobService) ChangeStatus(id int64, status int8) error {
	job, err := s.getByID(id)
	if err != nil {
		return err
	}
	if err = s.db.Model(&model.SysJob{}).Where("id = ?", id).Update("status", status).Error; err != nil {
		return err
	}
	if status == model.JobStatusRunning {
		return scheduler.Manager.Add(job.ID, job.JobName, job.InvokeTarget, job.CronExpression, job.JobParams)
	}
	scheduler.Manager.Remove(id)
	return nil
}

// RunOnce 立即触发一次执行（异步），不影响 cron 调度节奏
func (s *JobService) RunOnce(id int64) error {
	job, err := s.getByID(id)
	if err != nil {
		return err
	}
	return scheduler.Manager.RunOnce(job.ID, job.JobName, job.InvokeTarget, job.JobParams)
}

// PageJobLogs 分页查询执行日志
func (s *JobService) PageJobLogs(pageNum, pageSize int, jobID int64) (common.PageResult[model.SysJobLog], error) {
	var logs []model.SysJobLog
	var total int64
	q := s.db.Model(&model.SysJobLog{})
	if jobID > 0 {
		q = q.Where("job_id = ?", jobID)
	}
	if err := q.Count(&total).Error; err != nil {
		return common.PageResult[model.SysJobLog]{}, err
	}
	pg := common.PageQuery{PageNum: pageNum, PageSize: pageSize}
	pg.Normalize()
	if err := q.Offset(pg.Offset()).Limit(pg.PageSize).Order("id DESC").Find(&logs).Error; err != nil {
		return common.PageResult[model.SysJobLog]{}, err
	}
	return common.PageResult[model.SysJobLog]{Total: total, PageNum: pg.PageNum, PageSize: pg.PageSize, List: logs}, nil
}

// CleanLogs 物理删除所有执行日志（sys_job_log 无软删除，日志清理为物理删除）
func (s *JobService) CleanLogs() error {
	return s.db.Where("1 = 1").Delete(&model.SysJobLog{}).Error
}

// WriteLog 由调度器 OnJobDone 钩子调用，将执行结果写入 sys_job_log
func (s *JobService) WriteLog(jobID int64, jobName, invokeTarget, params string, status int8, msg string, costMs int64) {
	log := &model.SysJobLog{
		JobID:        jobID,
		JobName:      jobName,
		InvokeTarget: invokeTarget,
		JobParams:    params,
		Status:       status,
		JobMessage:   msg,
		CostMs:       costMs,
	}
	s.db.Create(log)
}

// LoadAndScheduleRunning 应用启动时将 status=0（运行中）的任务重新加载到调度器
func (s *JobService) LoadAndScheduleRunning() error {
	var jobs []model.SysJob
	if err := s.db.Where("status = ?", model.JobStatusRunning).Find(&jobs).Error; err != nil {
		return err
	}
	for _, job := range jobs {
		if err := scheduler.Manager.Add(job.ID, job.JobName, job.InvokeTarget, job.CronExpression, job.JobParams); err != nil {
			zap.L().Warn("启动时加载定时任务失败", zap.Int64("jobId", job.ID), zap.Error(err))
		}
	}
	return nil
}

func (s *JobService) getByID(id int64) (*model.SysJob, error) {
	var job model.SysJob
	if err := s.db.First(&job, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.ErrNotFound("任务")
		}
		return nil, err
	}
	return &job, nil
}
