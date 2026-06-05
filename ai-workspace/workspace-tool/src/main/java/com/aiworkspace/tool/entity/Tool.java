package com.aiworkspace.tool.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tool")
public class Tool extends BaseEntity {

    /** unique tool name referenced by agents */
    private String name;
    private String description;
    /** tool type: http / builtin */
    private String toolType;
    /** invocation endpoint for http tools */
    private String endpoint;
    /** JSON parameter schema / config */
    private String config;
    private Integer enabled;
    private Long createBy;
}
