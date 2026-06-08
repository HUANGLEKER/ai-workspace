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

/**
 * 消息管理控制器
 *
 * REST 路径前缀：/api/chat/message
 *
 * 提供会话消息的查询。消息归属通过其所属会话间接隔离，
 * 暴露消息前必须先校验调用者拥有该会话。
 *
 * @since 2026
 */
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

    /**
     * 获取指定会话的消息列表
     *
     * GET /api/chat/message/list
     *
     * @param sessionId 会话 ID
     * @return 该会话的全部消息（时间正序）
     */
    @Operation(summary = "获取会话消息列表")
    @GetMapping("/list")
    public Result<List<ChatMessage>> list(@RequestParam Long sessionId) {
        // 安全：暴露消息前先校验调用者拥有该会话，防止越权读取他人对话（IDOR）
        chatSessionService.getOwned(sessionId, currentUserId());
        return Result.ok(chatMessageService.listBySessionId(sessionId));
    }

    /**
     * 从 Spring Security 上下文取出当前登录用户 ID，作为归属隔离依据
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
