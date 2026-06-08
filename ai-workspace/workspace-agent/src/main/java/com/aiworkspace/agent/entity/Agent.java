package com.aiworkspace.agent.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 实体
 *
 * 对应 agent 表，表示用户私有的 Agent 定义；通过 createBy 隔离归属
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
public class Agent extends BaseEntity {

    private String name;
    private String description;
    /** 系统提示词，每次运行时注入在用户输入之前 */
    private String systemPrompt;
    /** chat 模型名；为空时回退到 AI 服务默认模型 */
    private String model;
    /** 工具中心工具名的 JSON 数组字符串，如 ["search","calc"]；运行时解析为完整 HTTP tool 规格 */
    private String tools;
    /** MCP server 名的 JSON 数组字符串，其工具在运行时加载 */
    private String mcpServers;
    /** 是否启用：1=启用，0=禁用 */
    private Integer enabled;
    /** 归属用户ID（资源隔离列） */
    private Long createBy;
}
