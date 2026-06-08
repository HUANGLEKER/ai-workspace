package com.aiworkspace.workflow.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流运行请求体
 *
 * 承载单次工作流运行的输入参数与会话标识
 */
@Data
public class WorkflowRunRequest {
    /** 传递给工作流的输入参数，如 {"prompt": "..."} */
    private Map<String, Object> inputs = new HashMap<>();
    /** 可选的会话ID，用于串联运行上下文；为空时由服务端生成默认值 */
    private String sessionId;
}
