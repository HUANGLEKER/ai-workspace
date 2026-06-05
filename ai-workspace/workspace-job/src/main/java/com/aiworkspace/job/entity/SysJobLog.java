package com.aiworkspace.job.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_job_log")
public class SysJobLog {

    /** execution result: 0 = success, 1 = failure */
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAIL = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String jobName;
    private String invokeTarget;
    private String jobParams;
    private Integer status;
    private String jobMessage;
    private String exceptionInfo;
    /** execution duration in milliseconds */
    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
