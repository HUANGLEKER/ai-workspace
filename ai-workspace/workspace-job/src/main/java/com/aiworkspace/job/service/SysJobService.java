package com.aiworkspace.job.service;

import com.aiworkspace.job.entity.SysJob;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SysJobService extends IService<SysJob> {

    Page<SysJob> pageJobs(int page, int size, String jobName);

    void addJob(SysJob job, Long userId);

    void updateJob(SysJob job);

    void deleteJob(Long id);

    void changeStatus(Long id, Integer status);

    /** trigger a job immediately, independent of its schedule */
    void runOnce(Long id);
}
