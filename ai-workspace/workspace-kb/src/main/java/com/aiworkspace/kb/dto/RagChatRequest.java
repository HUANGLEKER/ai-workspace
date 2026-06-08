package com.aiworkspace.kb.dto;

import lombok.Data;

/**
 * RAG 流式问答请求 DTO
 *
 * 承载 RAG 检索增强问答的入参，最终透传给 FastAPI 的 /rag/chat 端点。
 */
@Data
public class RagChatRequest {

    /** 目标知识库 ID（查询前需校验调用方归属） */
    private Long kbId;

    /** 用户提问 */
    private String question;

    /** 会话 ID，用于多轮上下文关联，缺省时回退为 "default" */
    private String sessionId;

    /** 向量检索召回的文档片段数（top-K），缺省回退为 4 */
    private Integer topK;
}
