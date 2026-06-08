package com.aiworkspace.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度器配置
 *
 * 为动态 cron 任务提供独立的 ThreadPoolTaskScheduler，与业务线程池隔离。
 */
@Configuration
public class JobSchedulerConfig {

    /**
     * 构建用于用户自定义 cron 任务的专用调度器
     *
     * @return ThreadPoolTaskScheduler 实例（线程池大小 5）
     */
    @Bean(name = "jobTaskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler jobTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("sys-job-");
        // 关闭时等待在途任务执行完成，最多等待 30 秒，避免任务被强制中断
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
