package com.aiworkspace.job.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.job.entity.SysJob;
import com.aiworkspace.job.handler.JobHandlerRegistry;
import com.aiworkspace.job.mapper.SysJobMapper;
import com.aiworkspace.job.scheduler.JobSchedulerManager;
import com.aiworkspace.job.service.SysJobService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysJobServiceImpl extends ServiceImpl<SysJobMapper, SysJob> implements SysJobService {

    private final JobSchedulerManager schedulerManager;
    private final JobHandlerRegistry handlerRegistry;

    public SysJobServiceImpl(JobSchedulerManager schedulerManager, JobHandlerRegistry handlerRegistry) {
        this.schedulerManager = schedulerManager;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public Page<SysJob> pageJobs(int page, int size, String jobName) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<SysJob>()
                        .like(StringUtils.hasText(jobName), SysJob::getJobName, jobName)
                        .orderByDesc(SysJob::getId));
    }

    @Override
    public void addJob(SysJob job, Long userId) {
        validate(job);
        job.setId(null);
        job.setCreateBy(userId);
        if (job.getStatus() == null) {
            job.setStatus(SysJob.STATUS_PAUSED);
        }
        save(job);
        schedulerManager.schedule(job);
    }

    @Override
    public void updateJob(SysJob job) {
        if (job.getId() == null) throw new BusinessException("任务ID不能为空");
        SysJob existing = getById(job.getId());
        if (existing == null) throw new BusinessException("任务不存在");
        validate(job);
        job.setCreateBy(existing.getCreateBy());
        updateById(job);
        // re-read to schedule with the persisted state
        schedulerManager.schedule(getById(job.getId()));
    }

    @Override
    public void deleteJob(Long id) {
        if (getById(id) == null) throw new BusinessException("任务不存在");
        schedulerManager.cancel(id);
        removeById(id);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        SysJob job = getById(id);
        if (job == null) throw new BusinessException("任务不存在");
        if (status == null || (status != SysJob.STATUS_RUNNING && status != SysJob.STATUS_PAUSED)) {
            throw new BusinessException("非法的任务状态");
        }
        job.setStatus(status);
        updateById(job);
        schedulerManager.schedule(job);
    }

    @Override
    public void runOnce(Long id) {
        SysJob job = getById(id);
        if (job == null) throw new BusinessException("任务不存在");
        schedulerManager.runOnce(job);
    }

    private void validate(SysJob job) {
        if (!StringUtils.hasText(job.getJobName())) {
            throw new BusinessException("任务名称不能为空");
        }
        if (!StringUtils.hasText(job.getInvokeTarget())) {
            throw new BusinessException("调用目标不能为空");
        }
        if (handlerRegistry.find(job.getInvokeTarget()).isEmpty()) {
            throw new BusinessException("未找到任务处理器: " + job.getInvokeTarget());
        }
        if (!StringUtils.hasText(job.getCronExpression())
                || !CronExpression.isValidExpression(job.getCronExpression())) {
            throw new BusinessException("Cron表达式不合法（需为Spring 6段格式：秒 分 时 日 月 周）");
        }
    }
}
