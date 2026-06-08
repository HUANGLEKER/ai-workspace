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

/**
 * RAG 问答 Controller
 *
 * REST 路径前缀：/api/rag
 * 提供基于知识库的 RAG 流式问答（SSE）与索引重建能力。
 *
 * 主要职责：
 * 1. RAG 流式问答：校验知识库归属后，将 FastAPI 的 SSE 流透传给浏览器
 * 2. 重建索引：切换 embedding 模型后重建知识库全部文档的向量
 *
 * SSE 代理任务运行在独立的 streamExecutor 线程池上（与嵌入线程池隔离，
 * 避免长连接流式占满线程饿死异步嵌入管道）。
 *
 * @author
 * @since 2026
 */
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

    /**
     * RAG 流式问答（SSE）
     *
     * POST /api/rag/chat（produces text/event-stream）
     *
     * 校验知识库归属后，组装请求体调用 FastAPI /rag/chat，并把其 SSE 流逐帧透传给浏览器：
     * token 帧统一包装为 {content} 增量文本；sources 元数据帧原样透传供前端渲染来源引用。
     *
     * @param request RAG 问答请求（知识库 ID、问题、会话、top-K）
     * @return SSE 发射器，超时 180s
     */
    @Operation(summary = "RAG流式问答（SSE）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody RagChatRequest request) {
        // 查询前校验调用方拥有目标知识库，防止越权读取他人知识库内容
        knowledgeBaseService.getOwned(request.getKbId(), currentUserId());
        SseEmitter emitter = new SseEmitter(180_000L);

        Map<String, Object> body = new HashMap<>();
        body.put("session_id", request.getSessionId() != null ? request.getSessionId() : "default");
        body.put("kb_id", String.valueOf(request.getKbId()));
        body.put("question", request.getQuestion());
        body.put("top_k", request.getTopK() != null ? request.getTopK() : 4);
        body.put("stream", true);

        // 在独立的 streamExecutor 线程上执行 SSE 代理，避免阻塞 Web 线程并与嵌入线程池隔离
        streamExecutor.execute(() -> {
            try {
                fastApiClient.stream("/rag/chat", body, line -> {
                    // 只处理 SSE 的 data: 行，忽略心跳/空行
                    if (!line.startsWith("data: ")) return;
                    String data = line.substring(6).trim();
                    try {
                        // 结束哨兵：透传 [DONE] 通知前端流结束
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

    /**
     * 重建知识库 RAG 索引（切换 embedding 模型后必须执行）
     *
     * POST /api/rag/rebuild
     *
     * 对指定知识库下的所有文档重新切片+embedding 并写入 ChromaDB。
     * 切换 embedding 模型后旧向量与新模型不兼容，必须调此接口重建，否则 RAG 检索语义失效。
     *
     * @param body 请求体，包含 kbId（知识库 ID）
     * @return 空结果
     * @throws com.aiworkspace.common.exception.BusinessException kbId 为空或无权操作时抛出
     */
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

    /** 从 Spring Security 上下文提取当前登录用户 ID，作为资源归属依据 */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
