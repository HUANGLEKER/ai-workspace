package com.aiworkspace.kb.controller;

import com.aiworkspace.kb.dto.RagChatRequest;
import com.aiworkspace.kb.service.EmbeddingService;
import com.aiworkspace.system.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Map;
import java.util.concurrent.Executor;

@Tag(name = "RAG问答")
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    @Value("${fastapi.base-url}")
    private String fastapiBaseUrl;

    public RagController(EmbeddingService embeddingService,
                         ObjectMapper objectMapper,
                         @Qualifier("taskExecutor") Executor taskExecutor) {
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Operation(summary = "RAG流式问答（SSE）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody RagChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        taskExecutor.execute(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("session_id", request.getSessionId() != null ? request.getSessionId() : "default");
                body.put("kb_id", String.valueOf(request.getKbId()));
                body.put("question", request.getQuestion());
                body.put("top_k", request.getTopK() != null ? request.getTopK() : 4);
                body.put("stream", true);

                String requestJson = objectMapper.writeValueAsString(body);

                URL url = java.net.URI.create(fastapiBaseUrl + "/rag/chat").toURL();
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
                        String type = node.has("type") ? node.get("type").asText() : "";
                        if ("token".equals(type) && node.hasNonNull("token")) {
                            String token = node.get("token").asText();
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of("content", token))));
                        } else {
                            // sources metadata — pass through for frontend awareness
                            emitter.send(SseEmitter.event().data(data));
                        }
                    }
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

    @Operation(summary = "重建知识库RAG索引")
    @PostMapping("/rebuild")
    public com.aiworkspace.common.response.Result<Void> rebuild(@RequestBody Map<String, Object> body) {
        Object kbIdObj = body.get("kbId");
        if (kbIdObj == null) {
            throw new com.aiworkspace.common.exception.BusinessException("kbId is required");
        }
        Long kbId = Long.valueOf(kbIdObj.toString());
        embeddingService.rebuildKb(kbId);
        return com.aiworkspace.common.response.Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
