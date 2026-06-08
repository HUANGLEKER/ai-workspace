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
 * 动态 cron 任务调度管理器
 *
 * 负责在运行时向 {@link ThreadPoolTaskScheduler} 动态注册/取消 cron 任务，
 * 并将每次执行结果记录到 sys_job_log。
 *
 * 主要职责：
 * 1. 应用启动时重新装载所有运行中的任务
 * 2. 任务新增/更新/状态变更时运行时（重）调度或取消
 * 3. 执行任务并写入执行日志（成功/失败均记录）
 */
@Component
public class JobSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerManager.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;
    private final JobHandlerRegistry handlerRegistry;

    /** jobId -> 已调度的 ScheduledFuture，用于按任务单独取消/重新调度；并发场景使用 ConcurrentHashMap */
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

    /**
     * 启动重装：在 Spring 上下文（及数据库）就绪后，重新调度所有运行中的任务
     *
     * 监听 ContextRefreshedEvent 而非构造时执行，确保此时 Mapper 与数据源均已可用。
     * 单个任务调度失败不影响其余任务。
     */
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

    /**
     * （重）调度一个任务
     *
     * 先取消已有触发器避免重复调度；仅对状态为 RUNNING 的任务进行调度。
     * synchronized 保证并发的增删调度操作互斥，防止状态错乱。
     *
     * @param job 任务定义（其 cronExpression 须为合法 Spring 6 段式表达式）
     */
    public synchronized void schedule(SysJob job) {
        // 先取消旧触发器，确保编辑 cron 或状态后不会出现重复调度
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

    /**
     * 取消指定任务的调度触发器（若已调度）
     *
     * @param jobId 任务 ID
     */
    public synchronized void cancel(Long jobId) {
        ScheduledFuture<?> future = scheduled.remove(jobId);
        if (future != null) {
            // 传 false：不中断正在执行的任务，仅取消后续触发
            future.cancel(false);
        }
    }

    /**
     * 立即执行一次任务（不影响其 cron 调度），在调度线程池中异步运行
     *
     * @param job 待执行的任务定义
     */
    public void runOnce(SysJob job) {
        taskScheduler.execute(() -> execute(job.getId()));
    }

    /**
     * 按 ID 执行任务
     *
     * 执行前重新读取数据行，确保已取消/已编辑的任务使用最新配置；
     * 解析并调用对应 JobHandler，无论成功失败均写入一条 sys_job_log。
     *
     * @param jobId 任务 ID
     */
    private void execute(Long jobId) {
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            // 任务已被删除，取消残留触发器，避免无效执行
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
            // 按 invokeTarget 从注册表解析对应 JobHandler bean
            JobHandler handler = handlerRegistry.find(job.getInvokeTarget())
                    .orElseThrow(() -> new IllegalStateException("未找到任务处理器: " + job.getInvokeTarget()));
            handler.execute(job.getJobParams());
            logEntry.setStatus(SysJobLog.STATUS_SUCCESS);
            logEntry.setJobMessage("执行成功");
        } catch (Throwable t) {
            logEntry.setStatus(SysJobLog.STATUS_FAIL);
            logEntry.setJobMessage("执行失败: " + t.getMessage());
            // 截断堆栈，避免超出 exception_info 列长度限制
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
