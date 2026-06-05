package com.aiworkspace.job.scheduler;

import com.aiworkspace.job.entity.SysJob;
import com.aiworkspace.job.entity.SysJobLog;
import com.aiworkspace.job.handler.JobHandler;
import com.aiworkspace.job.handler.JobHandlerRegistry;
import com.aiworkspace.job.mapper.SysJobLogMapper;
import com.aiworkspace.job.mapper.SysJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Registers/cancels cron tasks on a {@link ThreadPoolTaskScheduler} at runtime and records
 * each execution to {@code sys_job_log}. Running jobs are (re)scheduled on application startup.
 */
@Component
public class JobSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerManager.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;
    private final JobHandlerRegistry handlerRegistry;

    /** jobId -> scheduled future, so jobs can be cancelled/rescheduled individually */
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public JobSchedulerManager(@Qualifier("jobTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
                               SysJobMapper sysJobMapper,
                               SysJobLogMapper sysJobLogMapper,
                               JobHandlerRegistry handlerRegistry) {
        this.taskScheduler = taskScheduler;
        this.sysJobMapper = sysJobMapper;
        this.sysJobLogMapper = sysJobLogMapper;
        this.handlerRegistry = handlerRegistry;
    }

    /** Schedule all running jobs once the context (and DB) is ready. */
    @EventListener(ContextRefreshedEvent.class)
    public void scheduleExistingJobs() {
        List<SysJob> jobs = sysJobMapper.selectList(null);
        int count = 0;
        for (SysJob job : jobs) {
            if (job.getStatus() != null && job.getStatus() == SysJob.STATUS_RUNNING) {
                try {
                    schedule(job);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to schedule job {} ({})", job.getId(), job.getJobName(), e);
                }
            }
        }
        log.info("Scheduled {} running cron job(s) on startup", count);
    }

    /** (Re)schedule a job. Cancels any existing trigger first; only RUNNING jobs are armed. */
    public synchronized void schedule(SysJob job) {
        cancel(job.getId());
        if (job.getStatus() == null || job.getStatus() != SysJob.STATUS_RUNNING) {
            return;
        }
        CronTrigger trigger = new CronTrigger(job.getCronExpression());
        ScheduledFuture<?> future = taskScheduler.schedule(() -> execute(job.getId()), trigger);
        if (future != null) {
            scheduled.put(job.getId(), future);
        }
    }

    /** Cancel a job's trigger if scheduled. */
    public synchronized void cancel(Long jobId) {
        ScheduledFuture<?> future = scheduled.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** Run a job immediately, off the scheduling thread. */
    public void runOnce(SysJob job) {
        taskScheduler.execute(() -> execute(job.getId()));
    }

    /**
     * Execute a job by id. Re-reads the row so cancelled/edited jobs use current config,
     * invokes the handler, and writes a log entry regardless of outcome.
     */
    private void execute(Long jobId) {
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            cancel(jobId);
            return;
        }
        SysJobLog logEntry = new SysJobLog();
        logEntry.setJobId(job.getId());
        logEntry.setJobName(job.getJobName());
        logEntry.setInvokeTarget(job.getInvokeTarget());
        logEntry.setJobParams(job.getJobParams());

        long start = System.currentTimeMillis();
        try {
            JobHandler handler = handlerRegistry.find(job.getInvokeTarget())
                    .orElseThrow(() -> new IllegalStateException("未找到任务处理器: " + job.getInvokeTarget()));
            handler.execute(job.getJobParams());
            logEntry.setStatus(SysJobLog.STATUS_SUCCESS);
            logEntry.setJobMessage("执行成功");
        } catch (Throwable t) {
            logEntry.setStatus(SysJobLog.STATUS_FAIL);
            logEntry.setJobMessage("执行失败: " + t.getMessage());
            logEntry.setExceptionInfo(truncate(stackTrace(t), 2000));
            log.error("Job {} ({}) failed", job.getId(), job.getJobName(), t);
        } finally {
            logEntry.setCostMs(System.currentTimeMillis() - start);
            sysJobLogMapper.insert(logEntry);
        }
    }

    private static String stackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
