package com.aiworkspace.workflow.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workflow")
public class Workflow extends BaseEntity {

    private String name;
    private String description;
    /** JSON workflow definition (nodes/edges); interpreted by the AI service */
    private String definition;
    /** chat model name; falls back to the AI service default when blank */
    private String model;
    private Integer enabled;
    private Long createBy;
}
