package com.aiworkspace.job.service;

import com.aiworkspace.job.entity.SysJob;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定时任务服务接口
 *
 * 定义定时任务的 CRUD、状态切换与手动触发能力，并联动调度器完成运行时（重）调度。
 */
public interface SysJobService extends IService<SysJob> {

    /**
     * 分页查询任务列表
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param jobName 任务名称模糊过滤，可为空
     * @return 任务分页结果
     */
    Page<SysJob> pageJobs(int page, int size, String jobName);

    /**
     * 新增任务并按状态尝试调度
     *
     * @param job    任务定义（cron 须合法、invokeTarget 须存在）
     * @param userId 创建人用户 ID
     */
    void addJob(SysJob job, Long userId);

    /**
     * 更新任务并按持久化后的状态重新调度
     *
     * @param job 任务定义（须含 ID）
     */
    void updateJob(SysJob job);

    /**
     * 删除任务并取消其调度
     *
     * @param id 任务 ID
     */
    void deleteJob(Long id);

    /**
     * 切换任务启用/暂停状态并联动（重）调度或取消
     *
     * @param id     任务 ID
     * @param status 目标状态（0=运行，1=暂停）
     */
    void changeStatus(Long id, Integer status);

    /**
     * 立即触发一次执行，独立于其 cron 调度
     *
     * @param id 任务 ID
     */
    void runOnce(Long id);
}
