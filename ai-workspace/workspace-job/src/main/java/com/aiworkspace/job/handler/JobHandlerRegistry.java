package com.aiworkspace.job.handler;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务处理器注册表
 *
 * 将容器中所有 {@link JobHandler} bean 按名称建立索引，使任务在运行时
 * 能根据 invokeTarget 解析出对应处理器。可插拔：新增 JobHandler bean 即自动注册。
 */
@Component
public class JobHandlerRegistry {

    /** 处理器名称 -> 处理器实例 */
    private final Map<String, JobHandler> handlers;

    /**
     * 注入容器中全部 JobHandler bean 并按名称建立索引
     *
     * @param handlerBeans Spring 收集的所有 JobHandler 实现
     */
    public JobHandlerRegistry(List<JobHandler> handlerBeans) {
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(JobHandler::getName, Function.identity()));
    }

    /**
     * 按名称查找处理器
     *
     * @param name 处理器名称（对应 SysJob.invokeTarget）
     * @return 匹配的处理器，不存在时为空 Optional
     */
    public Optional<JobHandler> find(String name) {
        return Optional.ofNullable(handlers.get(name));
    }

    /**
     * 列出所有已注册处理器名称（供管理界面下拉选择）
     *
     * @return 按字典序排序的处理器名称列表
     */
    public List<String> names() {
        return handlers.keySet().stream().sorted().collect(Collectors.toList());
    }
}
