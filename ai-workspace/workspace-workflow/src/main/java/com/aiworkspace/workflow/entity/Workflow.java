package com.aiworkspace.workflow.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流实体
 *
 * 对应 workflow 表，表示用户私有的工作流定义；通过 createBy 隔离归属
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workflow")
public class Workflow extends BaseEntity {

    private String name;
    private String description;
    /** 工作流定义的 JSON 字符串（节点/边），由 FastAPI 的 LangGraph 引擎解释执行 */
    private String definition;
    /** chat 模型名；为空时回退到 AI 服务默认模型 */
    private String model;
    /** 是否启用：1=启用，0=禁用 */
    private Integer enabled;
    /** 归属用户ID（资源隔离列） */
    private Long createBy;
}
