package com.aiworkspace.framework.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 *
 * 通过 @EnableAsync 启用 Spring 异步能力，并定义两类相互隔离的线程池，
 * 避免不同性质的后台任务相互争抢与饿死。
 *
 * 主要职责：
 * 1. taskExecutor —— 承载嵌入管道等后台批处理任务
 * 2. streamExecutor —— 承载 Chat/RAG 的 SSE 长连接代理任务
 *
 * @author
 * @since 2026
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 后台任务线程池（CPU/IO 型）
     *
     * 主要承载嵌入管道（{@code @Async("taskExecutor")}）。刻意与
     * {@link #streamExecutor()} 隔离，确保大量长连接聊天流不会饿死嵌入任务（反之亦然）。
     *
     * @return 后台任务线程池
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心 5 / 最大 10 / 队列 100：嵌入任务量可控，用有界队列削峰
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-task-");
        // 线程池饱和时由调用线程亲自执行任务而非丢弃，保证嵌入任务不丢失（提供背压）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待在途任务完成，最多等待 30 秒
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 代理专用线程池（IO 型）
     *
     * 专用于 Chat/RAG 的 SSE 流式代理。此类线程为 IO 密集型，可能在整条流的生命周期内
     * 阻塞（最长约 3 分钟），故策略上偏向"多线程、零队列"：queueCapacity 设为 0
     * （SynchronousQueue），使每个并发流直接新建工作线程直至 maxPoolSize，
     * 超出后回退到调用线程执行，避免长任务在队列中积压导致响应延迟。
     *
     * @return SSE 流式代理线程池
     */
    @Bean(name = "streamExecutor")
    public Executor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心 20 / 最大 200 / 队列 0：以线程数应对高并发长连接，而非排队
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(0);
        // 空闲线程 60 秒后回收，且允许核心线程超时，避免空闲期占用大量线程资源
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
