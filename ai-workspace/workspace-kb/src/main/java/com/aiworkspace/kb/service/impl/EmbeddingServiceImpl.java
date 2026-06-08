package com.aiworkspace.kb.service.impl;

import com.aiworkspace.framework.client.FastApiClient;
import com.aiworkspace.kb.entity.KbChunkTask;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.mapper.KbChunkTaskMapper;
import com.aiworkspace.kb.mapper.KbDocumentMapper;
import com.aiworkspace.kb.service.EmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 嵌入（embedding）服务实现
 *
 * 驱动文档向量化的异步管道：调用 FastApiClient 请求 FastAPI 完成切片+embedding，
 * 结果写入 ChromaDB，并维护文档状态与 KbChunkTask 任务记录。
 *
 * 主要职责：
 * 1. 异步构建单文档向量索引（@Async("taskExecutor")）
 * 2. 重建知识库全部文档索引（切换 embedding 模型后必须执行）
 * 3. 删除文档对应向量
 *
 * @author
 * @since 2026
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkTaskMapper kbChunkTaskMapper;
    private final FastApiClient fastApiClient;

    /**
     * 自身代理引用（@Lazy 注入打破构造循环依赖）。
     * @Async 自调用（this.buildAsync）不经 Spring 代理会退化为同步执行，
     * 故 rebuildKb 中必须通过该 bean 引用调用，才能真正走 taskExecutor 线程池。
     */
    private final EmbeddingService self;

    public EmbeddingServiceImpl(KbDocumentMapper kbDocumentMapper,
                                 KbChunkTaskMapper kbChunkTaskMapper,
                                 FastApiClient fastApiClient,
                                 @Lazy EmbeddingService self) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkTaskMapper = kbChunkTaskMapper;
        this.fastApiClient = fastApiClient;
        this.self = self;
    }

    /**
     * 异步构建单文档向量索引，运行于 taskExecutor 嵌入线程池（与 SSE 流式的 streamExecutor 隔离，
     * 避免长连接饿死嵌入任务）。全程在独立线程上发起阻塞式 HTTP 调用，不阻塞调用方。
     *
     * 状态流转与任务记录：
     * 1. 插入 KbChunkTask（RUNNING）作为追踪记录
     * 2. 文档置 PROCESSING
     * 3. 调用 FastAPI 构建成功 → 任务 SUCCESS、文档 DONE
     * 4. 失败 → 任务 FAILED 并记录 errorMsg、文档 FAILED
     */
    @Async("taskExecutor")
    @Override
    public void buildAsync(KbDocument document) {
        // 新增任务追踪记录，初始 RUNNING
        KbChunkTask task = new KbChunkTask();
        task.setDocumentId(document.getId());
        task.setTaskStatus("RUNNING");
        kbChunkTaskMapper.insert(task);

        // 文档进入处理中状态
        updateDocStatus(document.getId(), "PROCESSING");

        try {
            callFastapiEmbedBuild(document);

            // 成功：任务与文档分别置终态 SUCCESS / DONE
            task.setTaskStatus("SUCCESS");
            kbChunkTaskMapper.updateById(task);
            updateDocStatus(document.getId(), "DONE");
        } catch (Exception e) {
            // 失败：记录错误信息以便排查，任务与文档置 FAILED
            log.error("Embedding failed for document {}: {}", document.getId(), e.getMessage());
            task.setTaskStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            kbChunkTaskMapper.updateById(task);
            updateDocStatus(document.getId(), "FAILED");
        }
    }

    @Override
    public void rebuildKb(Long kbId) {
        List<KbDocument> docs = kbDocumentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getKbId, kbId));
        for (KbDocument doc : docs) {
            // 经 self 代理引用调用 @Async 方法，确保真正异步（this. 自调用不走代理会变同步）
            self.buildAsync(doc);
        }
    }

    @Override
    public void deleteDocument(String kbId, String documentId) {
        // 向量删除失败仅告警、不抛出，避免阻断上层文档删除主流程（最终一致：脏向量可后续重建清理）
        try {
            Map<String, String> body = new HashMap<>();
            body.put("kb_id", kbId);
            body.put("document_id", documentId);
            fastApiClient.send("DELETE", "/embedding/delete", body, 30_000);
        } catch (Exception e) {
            log.warn("Failed to delete embeddings for document {}: {}", documentId, e.getMessage());
        }
    }

    /** 组装请求体并调用 FastAPI /embedding/build 完成切片+embedding 写入 ChromaDB */
    private void callFastapiEmbedBuild(KbDocument doc) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("kb_id", String.valueOf(doc.getKbId()));
        body.put("document_id", String.valueOf(doc.getId()));
        body.put("file_path", doc.getFilePath());
        body.put("file_name", doc.getFileName());
        body.put("chunk_size", 500);
        body.put("chunk_overlap", 50);

        // 大文档切片+embedding 耗时较长，超时放宽到 5 分钟
        fastApiClient.send("POST", "/embedding/build", body, 300_000);
    }

    /** 仅更新文档状态列，驱动 PENDING→PROCESSING→DONE/FAILED 状态流转 */
    private void updateDocStatus(Long docId, String status) {
        kbDocumentMapper.update(null,
                new LambdaUpdateWrapper<KbDocument>()
                        .eq(KbDocument::getId, docId)
                        .set(KbDocument::getStatus, status));
    }
}
