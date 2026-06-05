package com.aiworkspace.agent.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
public class Agent extends BaseEntity {

    private String name;
    private String description;
    /** system prompt injected ahead of the user input on each run */
    private String systemPrompt;
    /** chat model name; falls back to the AI service default when blank */
    private String model;
    /** JSON array string of Tool Center tool names, e.g. ["search","calc"] */
    private String tools;
    /** JSON array string of MCP server names whose tools are loaded at run time */
    private String mcpServers;
    private Integer enabled;
    private Long createBy;
}
