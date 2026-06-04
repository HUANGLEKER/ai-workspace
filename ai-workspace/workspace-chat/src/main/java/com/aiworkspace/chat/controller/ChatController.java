package com.aiworkspace.chat.controller;

import com.aiworkspace.chat.dto.SendMessageRequest;
import com.aiworkspace.chat.entity.ChatMessage;
import com.aiworkspace.chat.service.ChatMessageService;
import com.aiworkspace.system.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Tag(name = "Chat对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    @Value("${fastapi.base-url}")
    private String fastapiBaseUrl;

    public ChatController(ChatMessageService chatMessageService,
                          ObjectMapper objectMapper,
                          @Qualifier("taskExecutor") Executor taskExecutor) {
        this.chatMessageService = chatMessageService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Operation(summary = "发送消息（SSE流式）")
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody @Valid SendMessageRequest request) {
        Long userId = currentUserId();
        SseEmitter emitter = new SseEmitter(180_000L);

        // save user message
        chatMessageService.saveMessage(request.getSessionId(), "user", request.getContent());

        // load full history for context
        List<ChatMessage> history = chatMessageService.listBySessionId(request.getSessionId());

        taskExecutor.execute(() -> {
            StringBuilder assistantReply = new StringBuilder();
            try {
                // build FastAPI request body
                List<Map<String, String>> messages = history.stream()
                        .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                        .toList();

                Map<String, Object> body = new HashMap<>();
                body.put("session_id", String.valueOf(request.getSessionId()));
                body.put("messages", messages);
                body.put("stream", true);

                String requestJson = objectMapper.writeValueAsString(body);

                URL url = java.net.URI.create(fastapiBaseUrl + "/chat").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(180_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestJson.getBytes(StandardCharsets.UTF_8));
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            break;
                        }
                        JsonNode node = objectMapper.readTree(data);
                        if (node.hasNonNull("token")) {
                            String token = node.get("token").asText();
                            assistantReply.append(token);
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of("content", token))));
                        }
                    }
                }

                // persist complete assistant response
                if (!assistantReply.isEmpty()) {
                    chatMessageService.saveMessage(request.getSessionId(), "assistant", assistantReply.toString());
                }

                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
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
