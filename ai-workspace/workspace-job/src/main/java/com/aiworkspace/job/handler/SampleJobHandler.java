package com.aiworkspace.job.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 内置示例任务处理器
 *
 * 使调度器开箱即用：每次触发输出一行心跳日志，可作为编写真实任务类型的模板。
 */
@Component
public class SampleJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SampleJobHandler.class);

    /**
     * @return 处理器名称 sampleJob，供任务的 invokeTarget 引用
     */
    @Override
    public String getName() {
        return "sampleJob";
    }

    /**
     * 执行示例任务：打印一行心跳日志
     *
     * @param params 任务配置的参数字符串，原样输出到日志
     */
    @Override
    public void execute(String params) {
        log.info("[SampleJob] heartbeat at {} params={}", LocalDateTime.now(), params);
    }
}
