package com.aiworkspace.chat.controller;

import com.aiworkspace.chat.entity.ChatMessage;
import com.aiworkspace.chat.service.ChatMessageService;
import com.aiworkspace.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息管理")
@RestController
@RequestMapping("/api/chat/message")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @Operation(summary = "获取会话消息列表")
    @GetMapping("/list")
    public Result<List<ChatMessage>> list(@RequestParam Long sessionId) {
        return Result.ok(chatMessageService.listBySessionId(sessionId));
    }
}
