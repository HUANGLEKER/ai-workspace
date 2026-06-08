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

/**
 * 会话管理控制器
 *
 * REST 路径前缀：/api/chat/session
 *
 * 提供会话的列表、创建与删除。会话为用户私有资源，
 * 所有操作均以当前登录用户 ID 做归属隔离。
 *
 * @since 2026
 */
@Tag(name = "会话管理")
@RestController
@RequestMapping("/api/chat/session")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * 获取当前用户的会话列表
     *
     * GET /api/chat/session/list
     *
     * @return 仅包含当前用户自有会话的列表
     */
    @Operation(summary = "获取会话列表")
    @GetMapping("/list")
    public Result<List<ChatSession>> list() {
        Long userId = currentUserId();
        return Result.ok(chatSessionService.listByUserId(userId));
    }

    /**
     * 创建会话
     *
     * POST /api/chat/session/create
     *
     * @param request 标题与模型选择
     * @return 新建的会话（归属当前用户）
     */
    @Operation(summary = "创建会话")
    @PostMapping("/create")
    public Result<ChatSession> create(@RequestBody CreateSessionRequest request) {
        Long userId = currentUserId();
        ChatSession session = chatSessionService.createSession(userId, request.getTitle(), request.getModelName());
        return Result.ok(session);
    }

    /**
     * 删除会话
     *
     * DELETE /api/chat/session/delete/{id}
     *
     * @param id 会话 ID；服务层会先校验归属，越权删除将被拒绝
     * @return 操作结果
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = currentUserId();
        chatSessionService.deleteSession(id, userId);
        return Result.ok();
    }

    /**
     * 从 Spring Security 上下文取出当前登录用户 ID，作为归属隔离依据
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
