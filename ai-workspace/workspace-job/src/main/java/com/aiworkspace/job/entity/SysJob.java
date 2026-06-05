package com.aiworkspace.job.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    /** job status: 0 = scheduled/running, 1 = paused */
    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_PAUSED = 1;

    private String jobName;
    private String jobGroup;
    /** name of the {@code JobHandler} bean to invoke */
    private String invokeTarget;
    /** Spring 6-field cron expression: second minute hour day-of-month month day-of-week */
    private String cronExpression;
    /** optional string parameter passed to the handler */
    private String jobParams;
    private Integer status;
    private String remark;
    private Long createBy;
}
