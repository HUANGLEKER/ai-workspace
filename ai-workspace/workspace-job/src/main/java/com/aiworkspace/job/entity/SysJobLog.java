package com.aiworkspace.job.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志实体（对应表 sys_job_log）
 *
 * 每次任务执行（成功或失败）追加一条记录。该表为只追加模型：
 * 没有 deleted 软删除列，"清空日志"为物理删除；仅填充 create_time。
 */
@Data
@TableName("sys_job_log")
public class SysJobLog {

    /** 执行结果：0=成功 */
    public static final int STATUS_SUCCESS = 0;
    /** 执行结果：1=失败 */
    public static final int STATUS_FAIL = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String jobName;
    private String invokeTarget;
    private String jobParams;
    /** 执行结果，取值见 STATUS_SUCCESS / STATUS_FAIL */
    private Integer status;
    private String jobMessage;
    /** 失败时的异常堆栈（已截断，最多 2000 字符） */
    private String exceptionInfo;
    /** 执行耗时（毫秒） */
    private Long costMs;

    // 仅追加日志，仅在插入时填充创建时间，无更新时间
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
