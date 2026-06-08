package com.aiworkspace.agent.dto;

import lombok.Data;

/**
 * Agent 运行请求体
 *
 * 承载单次 Agent 运行的用户输入与会话标识
 */
@Data
public class AgentRunRequest {
    /** 本次运行的用户输入 */
    private String input;
    /** 可选的会话ID，用于串联多轮上下文；为空时由服务端生成默认值 */
    private String sessionId;
}
