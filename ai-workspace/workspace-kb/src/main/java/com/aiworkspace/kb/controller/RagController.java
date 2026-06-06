package com.aiworkspace.kb.controller;

import com.aiworkspace.framework.client.FastApiClient;
import com.aiworkspace.kb.dto.RagChatRequest;
import com.aiworkspace.kb.service.EmbeddingService;
import com.aiworkspace.kb.service.KnowledgeBaseService;
import com.aiworkspace.system.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Tag(name = "RAG问答")
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final EmbeddingService embeddingService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;
    private final FastApiClient fastApiClient;
    private final Executor streamExecutor;

    public RagController(EmbeddingService embeddingService,
                         KnowledgeBaseService knowledgeBaseService,
                         ObjectMapper objectMapper,
                         FastApiClient fastApiClient,
                         @Qualifier("streamExecutor") Executor streamExecutor) {
        this.embeddingService = embeddingService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
        this.fastApiClient = fastApiClient;
        this.streamExecutor = streamExecutor;
    }

    @Operation(summary = "RAG流式问答（SSE）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody RagChatRequest request) {
        // verify the caller owns the target knowledge base before querying it
        knowledgeBaseService.getOwned(request.getKbId(), currentUserId());
        SseEmitter emitter = new SseEmitter(180_000L);

        Map<String, Object> body = new HashMap<>();
        body.put("session_id", request.getSessionId() != null ? request.getSessionId() : "default");
        body.put("kb_id", String.valueOf(request.getKbId()));
        body.put("question", request.getQuestion());
        body.put("top_k", request.getTopK() != null ? request.getTopK() : 4);
        body.put("stream", true);

        streamExecutor.execute(() -> {
            try {
                fastApiClient.stream("/rag/chat", body, line -> {
                    if (!line.startsWith("data: ")) return;
                    String data = line.substring(6).trim();
                    try {
                        if ("[DONE]".equals(data)) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            return;
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
        knowledgeBaseService.getOwned(kbId, currentUserId());
        embeddingService.rebuildKb(kbId);
        return com.aiworkspace.common.response.Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
