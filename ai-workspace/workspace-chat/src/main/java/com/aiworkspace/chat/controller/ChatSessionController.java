package com.aiworkspace.chat.controller;

import com.aiworkspace.chat.dto.CreateSessionRequest;
import com.aiworkspace.chat.entity.ChatSession;
import com.aiworkspace.chat.service.ChatSessionService;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.system.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "会话管理")
@RestController
@RequestMapping("/api/chat/session")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/list")
    public Result<List<ChatSession>> list() {
        Long userId = currentUserId();
        return Result.ok(chatSessionService.listByUserId(userId));
    }

    @Operation(summary = "创建会话")
    @PostMapping("/create")
    public Result<ChatSession> create(@RequestBody CreateSessionRequest request) {
        Long userId = currentUserId();
        ChatSession session = chatSessionService.createSession(userId, request.getTitle(), request.getModelName());
        return Result.ok(session);
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = currentUserId();
        chatSessionService.deleteSession(id, userId);
        return Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
