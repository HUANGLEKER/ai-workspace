package com.aiworkspace.chat.controller;

import com.aiworkspace.chat.entity.ChatMessage;
import com.aiworkspace.chat.service.ChatMessageService;
import com.aiworkspace.chat.service.ChatSessionService;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.system.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息管理")
@RestController
@RequestMapping("/api/chat/message")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;

    public ChatMessageController(ChatMessageService chatMessageService,
                                 ChatSessionService chatSessionService) {
        this.chatMessageService = chatMessageService;
        this.chatSessionService = chatSessionService;
    }

    @Operation(summary = "获取会话消息列表")
    @GetMapping("/list")
    public Result<List<ChatMessage>> list(@RequestParam Long sessionId) {
        // ensure the caller owns the session before exposing its messages
        chatSessionService.getOwned(sessionId, currentUserId());
        return Result.ok(chatMessageService.listBySessionId(sessionId));
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
