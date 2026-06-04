package com.aiworkspace.kb.service.impl;

import com.aiworkspace.kb.entity.KbChunkTask;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.mapper.KbChunkTaskMapper;
import com.aiworkspace.kb.mapper.KbDocumentMapper;
import com.aiworkspace.kb.service.EmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkTaskMapper kbChunkTaskMapper;
    private final ObjectMapper objectMapper;
    private final EmbeddingService self;

    @Value("${fastapi.base-url}")
    private String fastapiBaseUrl;

    public EmbeddingServiceImpl(KbDocumentMapper kbDocumentMapper,
                                 KbChunkTaskMapper kbChunkTaskMapper,
                                 ObjectMapper objectMapper,
                                 @Lazy EmbeddingService self) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkTaskMapper = kbChunkTaskMapper;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Async("taskExecutor")
    @Override
    public void buildAsync(KbDocument document) {
        // create task record
        KbChunkTask task = new KbChunkTask();
        task.setDocumentId(document.getId());
        task.setTaskStatus("RUNNING");
        kbChunkTaskMapper.insert(task);

        // update document status
        updateDocStatus(document.getId(), "PROCESSING");

        try {
            callFastapiEmbedBuild(document);

            task.setTaskStatus("SUCCESS");
            kbChunkTaskMapper.updateById(task);
            updateDocStatus(document.getId(), "DONE");
        } catch (Exception e) {
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
            self.buildAsync(doc);
        }
    }

    @Override
    public void deleteDocument(String kbId, String documentId) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("kb_id", kbId);
            body.put("document_id", documentId);
            String json = objectMapper.writeValueAsString(body);

            URL url = new URL(fastapiBaseUrl + "/embedding/delete");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
        } catch (Exception e) {
            log.warn("Failed to delete embeddings for document {}: {}", documentId, e.getMessage());
        }
    }

    private void callFastapiEmbedBuild(KbDocument doc) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("kb_id", String.valueOf(doc.getKbId()));
        body.put("document_id", String.valueOf(doc.getId()));
        body.put("file_path", doc.getFilePath());
        body.put("file_name", doc.getFileName());
        body.put("chunk_size", 500);
        body.put("chunk_overlap", 50);

        String json = objectMapper.writeValueAsString(body);

        URL url = new URL(fastapiBaseUrl + "/embedding/build");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(300_000); // 5 min for large docs

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new RuntimeException("FastAPI embedding/build failed, HTTP " + code);
        }
    }

    private void updateDocStatus(Long docId, String status) {
        kbDocumentMapper.update(null,
                new LambdaUpdateWrapper<KbDocument>()
                        .eq(KbDocument::getId, docId)
                        .set(KbDocument::getStatus, status));
    }
}
