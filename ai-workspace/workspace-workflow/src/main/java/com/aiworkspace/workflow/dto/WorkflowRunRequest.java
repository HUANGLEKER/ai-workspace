package com.aiworkspace.workflow.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WorkflowRunRequest {
    /** input parameters passed to the workflow (e.g. {"prompt": "..."}) */
    private Map<String, Object> inputs = new HashMap<>();
    /** optional session id to thread the run; defaults applied server-side */
    private String sessionId;
}
