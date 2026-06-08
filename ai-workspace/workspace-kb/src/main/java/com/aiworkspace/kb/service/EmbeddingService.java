package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbDocument;

/**
 * 嵌入（embedding）服务接口
 *
 * 封装文档向量化管道：调用 FastAPI 完成切片、embedding 并写入 ChromaDB。
 * 注意：buildAsync 为 @Async 方法，Spring 代理仅对外部 bean 调用生效，
 * 内部需重建索引时必须经注入的 bean 引用调用，不能用 this. 自调用（否则不走线程池、退化为同步）。
 */
public interface EmbeddingService {

    /**
     * 异步为单个文档构建向量索引（运行在 taskExecutor 嵌入线程池上）。
     * 期间驱动文档状态 PENDING→PROCESSING→DONE/FAILED 并写入 KbChunkTask 任务记录。
     *
     * @param document 待向量化的文档
     */
    void buildAsync(KbDocument document);

    /**
     * 重建知识库内所有文档的向量索引。
     * 切换 embedding 模型后必须重建（旧向量与新模型维度/语义不兼容）。
     *
     * @param kbId 知识库 ID
     */
    void rebuildKb(Long kbId);

    /**
     * 删除文档在 ChromaDB 中的向量数据（删除文档时同步清理，避免脏向量被检索）
     *
     * @param kbId       知识库 ID
     * @param documentId 文档 ID
     */
    void deleteDocument(String kbId, String documentId);
}
