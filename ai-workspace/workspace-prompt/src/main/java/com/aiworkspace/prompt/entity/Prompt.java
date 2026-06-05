package com.aiworkspace.prompt.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt")
public class Prompt extends BaseEntity {

    private String title;
    /** prompt template body */
    private String content;
    /** free-form category for grouping, e.g. "写作", "编程" */
    private String category;
    private String description;
    private Long createBy;
}
