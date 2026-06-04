package com.aiworkspace.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "sessionId不能为空")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
