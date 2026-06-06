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

@Tag(name = "Chat对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Upper bound on how many recent messages are replayed to the LLM as context. */
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

    @Operation(summary = "发送消息（SSE流式）")
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody @Valid SendMessageRequest request) {
        Long userId = currentUserId();
        // ensure the caller owns the session before reading/writing its messages
        chatSessionService.getOwned(request.getSessionId(), userId);
        SseEmitter emitter = new SseEmitter(180_000L);

        // save user message
        chatMessageService.saveMessage(request.getSessionId(), "user", request.getContent());

        // load a bounded slice of recent history for context (keeps LLM token cost
        // and latency from growing unbounded with conversation length)
        List<ChatMessage> history =
                chatMessageService.listRecentBySessionId(request.getSessionId(), MAX_CONTEXT_MESSAGES);

        // build FastAPI request body
        List<Map<String, String>> messages = history.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", String.valueOf(request.getSessionId()));
        body.put("messages", messages);
        body.put("stream", true);

        streamExecutor.execute(() -> {
            StringBuilder assistantReply = new StringBuilder();
            try {
                fastApiClient.stream("/chat", body, line -> {
                    if (!line.startsWith("data: ")) return;
                    String data = line.substring(6).trim();
                    try {
                        if ("[DONE]".equals(data)) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            return;
                        }
                        JsonNode node = objectMapper.readTree(data);
                        if (node.hasNonNull("token")) {
                            String token = node.get("token").asText();
                            assistantReply.append(token);
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of("content", token))));
                        }
                    } catch (Exception e) {
                        // client disconnected or serialization failed — stop streaming
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
                // persist whatever was generated, even if the client disconnected mid-stream
                if (!assistantReply.isEmpty()) {
                    try {
                        chatMessageService.saveMessage(request.getSessionId(), "assistant", assistantReply.toString());
                    } catch (Exception ex) {
                        // best-effort persistence; do not surface to the (already-closed) stream
                    }
                }
            }
        });

        return emitter;
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
