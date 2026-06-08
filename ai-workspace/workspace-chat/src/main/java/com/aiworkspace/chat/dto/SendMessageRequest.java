package com.aiworkspace.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送消息请求参数
 *
 * 承载 SSE 流式发送接口的入参，并通过 Jakarta Validation 做基础非空校验。
 */
@Data
public class SendMessageRequest {

    /** 目标会话 ID，服务端据此做归属校验后才允许读写消息 */
    @NotNull(message = "sessionId不能为空")
    private Long sessionId;

    /** 用户输入的消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
