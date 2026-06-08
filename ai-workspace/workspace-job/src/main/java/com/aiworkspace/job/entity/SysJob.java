package com.aiworkspace.job.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务定义实体（对应表 sys_job）
 *
 * 描述一个由动态 cron 调度器管理的可调度任务，仅管理员可维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    /** 任务状态：0=运行/已调度 */
    public static final int STATUS_RUNNING = 0;
    /** 任务状态：1=暂停（不参与调度） */
    public static final int STATUS_PAUSED = 1;

    private String jobName;
    private String jobGroup;
    /** 调用目标：待执行的 JobHandler bean 名称，运行时经 JobHandlerRegistry 按名解析 */
    private String invokeTarget;
    /** Spring 6 段式 cron 表达式：秒 分 时 日 月 周（新增/更新时经 CronExpression 校验合法性） */
    private String cronExpression;
    /** 传递给 JobHandler 的可选字符串参数 */
    private String jobParams;
    /** 任务状态，取值见 STATUS_RUNNING / STATUS_PAUSED */
    private Integer status;
    private String remark;
    /** 创建人用户 ID（任务按 createBy 归属，但仅管理员可访问） */
    private Long createBy;
}
