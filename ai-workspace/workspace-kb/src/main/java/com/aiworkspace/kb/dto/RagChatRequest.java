package com.aiworkspace.kb.dto;

import lombok.Data;

@Data
public class RagChatRequest {
    private Long kbId;
    private String question;
    private String sessionId;
    private Integer topK;
}
