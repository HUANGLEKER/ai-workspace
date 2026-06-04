package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbDocument;

import java.util.List;

public interface EmbeddingService {

    /** 异步为单个文档构建向量索引 */
    void buildAsync(KbDocument document);

    /** 重建知识库内所有文档的向量索引 */
    void rebuildKb(Long kbId);

    /** 删除文档的向量数据 */
    void deleteDocument(String kbId, String documentId);
}
