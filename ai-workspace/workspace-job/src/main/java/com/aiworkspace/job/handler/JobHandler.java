package com.aiworkspace.job.handler;

/**
 * 定时任务处理器接口
 *
 * 表示一个可被 SysJob 调度执行的工作单元。实现类须为 Spring bean（标注 @Component），
 * 任务的 invokeTarget 列存储处理器的 {@link #getName()}，运行时经 JobHandlerRegistry 按名解析。
 * 新增任务类型只需实现本接口即可，无需改动调度核心。
 */
public interface JobHandler {

    /**
     * 处理器唯一名称
     *
     * @return 名称，被 SysJob.invokeTarget 引用，并用于注册表索引
     */
    String getName();

    /**
     * 执行任务
     *
     * @param params 任务上配置的可选自由格式参数字符串
     * @throws Exception 任意失败均向上抛出，由调度器记录为一次失败执行日志
     */
    void execute(String params) throws Exception;
}
