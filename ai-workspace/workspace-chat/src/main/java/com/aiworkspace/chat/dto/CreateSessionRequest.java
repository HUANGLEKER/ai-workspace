package com.aiworkspace.chat.dto;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private String title;
    private String modelName;
}
