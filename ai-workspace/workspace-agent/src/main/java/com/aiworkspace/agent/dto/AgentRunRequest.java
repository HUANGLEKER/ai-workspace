package com.aiworkspace.agent.dto;

import lombok.Data;

@Data
public class AgentRunRequest {
    /** user input for this run */
    private String input;
    /** optional session id to thread the run; defaults applied server-side */
    private String sessionId;
}
