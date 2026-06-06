package com.aiworkspace.framework.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Pool for CPU/IO-bound background jobs — chiefly the embedding pipeline
     * ({@code @Async("taskExecutor")}). Kept deliberately separate from
     * {@link #streamExecutor()} so a flood of long-lived chat streams can never
     * starve embedding work (and vice versa).
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-task-");
        // When saturated, run the task on the calling thread instead of dropping it.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Pool dedicated to SSE proxy tasks (Chat / RAG streaming). These threads are
     * I/O-bound and may block for the full stream lifetime (up to ~3 min), so the
     * pool favors many threads over a deep queue: a {@code SynchronousQueue}
     * (queueCapacity 0) makes the pool spawn a new worker per concurrent stream up
     * to {@code maxPoolSize}, then fall back to running on the caller thread.
     */
    @Bean(name = "streamExecutor")
    public Executor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
