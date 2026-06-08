package com.aiworkspace.chat.controller;

import com.aiworkspace.chat.dto.SendMessageRequest;
import com.aiworkspace.chat.entity.ChatMessage;
import com.aiworkspace.chat.service.ChatMessageService;
import com.aiworkspace.chat.service.ChatSessionService;
import com.aiworkspace.framework.client.FastApiClient;
import com.aiworkspace.system.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Chat 对话控制器
 *
 * REST 路径前缀：/api/chat
 *
 * 主要职责：
 * 1. 接收用户消息并以 SSE 流式返回 LLM 回复（透传 FastAPI 的 /chat 流）
 * 2. 持久化用户消息与模型回复，并对发送给 LLM 的上下文做有界化
 * 3. 通过会话归属校验保证用户私有隔离
 *
 * @since 2026
 */
@Tag(name = "Chat对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 回放给 LLM 的最近上下文消息条数上限，用于控制 token 成本与延迟 */
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final ChatMessageService chatMessageService;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;
    private final FastApiClient fastApiClient;
    private final Executor streamExecutor;

    public ChatController(ChatMessageService chatMessageService,
                          ChatSessionService chatSessionService,
                          ObjectMapper objectMapper,
                          FastApiClient fastApiClient,
                          @Qualifier("streamExecutor") Executor streamExecutor) {
        this.chatMessageService = chatMessageService;
        this.chatSessionService = chatSessionService;
        this.objectMapper = objectMapper;
        this.fastApiClient = fastApiClient;
        this.streamExecutor = streamExecutor;
    }

    /**
     * 发送消息并以 SSE 流式返回 LLM 回复
     *
     * POST /api/chat/send
     *
     * 流程：
     * 1. 校验会话归属（防 IDOR）
     * 2. 持久化用户消息
     * 3. 加载有界上下文历史（最近 MAX_CONTEXT_MESSAGES 条，防 token 超限）
     * 4. 通过 FastApiClient 将 SSE 流透传到浏览器
     * 5. 无论客户端是否中途断连，均在 finally 块中保存已生成的回复（断连保存）
     *
     * @param request 含 sessionId 与消息内容的请求体
     * @return SseEmitter，向客户端推送 token 流，结束时发送 [DONE] 哨兵
     */
    @Operation(summary = "发送消息（SSE流式）")
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody @Valid SendMessageRequest request) {
        Long userId = currentUserId();
        // 归属校验：读写消息前必须确认调用者拥有该会话，防止越权访问他人对话（IDOR）
        chatSessionService.getOwned(request.getSessionId(), userId);
        SseEmitter emitter = new SseEmitter(180_000L);

        // 先持久化用户消息，确保即使后续流失败也有记录
        chatMessageService.saveMessage(request.getSessionId(), "user", request.getContent());

        // 有界上下文：仅取最近 N 条，避免 token 成本与延迟随会话长度无限增长
        List<ChatMessage> history =
                chatMessageService.listRecentBySessionId(request.getSessionId(), MAX_CONTEXT_MESSAGES);

        // 组装 FastAPI 请求体；FastAPI 不读 MySQL，模型名由 chat_model 表在会话层选定后随请求透传
        List<Map<String, String>> messages = history.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", String.valueOf(request.getSessionId()));
        body.put("messages", messages);
        body.put("stream", true);

        // 使用独立的 streamExecutor 执行 SSE 代理，避免长连接占用公共线程池、饿死嵌入任务
        streamExecutor.execute(() -> {
            StringBuilder assistantReply = new StringBuilder();
            try {
                fastApiClient.stream("/chat", body, line -> {
                    if (!line.startsWith("data: ")) return;
                    String data = line.substring(6).trim();
                    try {
                        // [DONE] 哨兵表示 FastAPI 已推送完毕，透传给浏览器后即可结束 SSE
                        if ("[DONE]".equals(data)) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            return;
                        }
                        JsonNode node = objectMapper.readTree(data);
                        if (node.hasNonNull("token")) {
                            // 逐 token 拼接，同时实时推送到浏览器
                            String token = node.get("token").asText();
                            assistantReply.append(token);
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of("content", token))));
                        }
                    } catch (Exception e) {
                        // 客户端断连或序列化失败时终止流
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            } finally {
                // 断连保存：无论客户端是否中途断开，只要有已生成内容就持久化，避免对话记录丢失
                if (!assistantReply.isEmpty()) {
                    try {
                        chatMessageService.saveMessage(request.getSessionId(), "assistant", assistantReply.toString());
                    } catch (Exception ex) {
                        // 尽力保存；此时 SSE 连接已关闭，异常不再上报
                    }
                }
            }
        });

        return emitter;
    }

    /**
     * 从 Spring Security 上下文取出当前登录用户 ID，作为归属隔离依据
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
