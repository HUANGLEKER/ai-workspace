package com.aiworkspace.kb.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.mapper.KbDocumentMapper;
import com.aiworkspace.kb.service.EmbeddingService;
import com.aiworkspace.kb.service.KbDocumentService;
import com.aiworkspace.kb.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 知识库文档服务实现
 *
 * 负责文档的上传/查询/删除全流程，串联三处外部资源：
 * 数据库（元信息）、MinIO（文件本体）、ChromaDB（向量，经 EmbeddingService）。
 *
 * 主要职责：
 * 1. 文档上传：存 MinIO + 落库 + 触发异步 embedding 构建
 * 2. 文档删除：清理 MinIO 对象与 ChromaDB 向量后再删库
 * 3. 按知识库归属隔离的列表/分页查询
 *
 * @author
 * @since 2026
 */
@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
        implements KbDocumentService {

    private final MinioClient minioClient;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Value("${minio.bucket}")
    private String bucket;

    public KbDocumentServiceImpl(MinioClient minioClient,
                                 EmbeddingService embeddingService,
                                 KnowledgeBaseService knowledgeBaseService) {
        this.minioClient = minioClient;
        this.embeddingService = embeddingService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public Page<KbDocument> pageByKbId(Long kbId, int page, int size, Long userId) {
        // 先校验调用方对知识库的归属，再分页查询其下文档
        knowledgeBaseService.getOwned(kbId, userId);
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .orderByDesc(KbDocument::getCreateTime));
    }

    @Override
    public KbDocument upload(Long kbId, MultipartFile file, Long userId) {
        // 校验调用方拥有目标知识库，防止向他人知识库注入文档
        knowledgeBaseService.getOwned(kbId, userId);
        String originalName = file.getOriginalFilename();
        String extension = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase()
                : "";
        // 用 UUID 重命名落盘，避免同名文件覆盖；按 kbId 分目录便于归属与清理
        String storedName = UUID.randomUUID() + "." + extension;
        String filePath = "kb/" + kbId + "/" + storedName;

        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }

        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        doc.setFileName(originalName);
        doc.setFilePath(filePath);
        doc.setFileSize(file.getSize());
        doc.setFileType(extension);
        // 初始状态 PENDING，等待异步 embedding 管道流转
        doc.setStatus("PENDING");
        save(doc);

        // 触发异步向量化构建（运行于 taskExecutor 线程池，不阻塞上传响应）
        embeddingService.buildAsync(doc);

        return doc;
    }

    @Override
    public void delete(Long id, Long userId) {
        KbDocument doc = getById(id);
        if (doc == null) throw new BusinessException("文档不存在");
        // 经所属知识库校验归属，防止删除他人文档
        knowledgeBaseService.getOwned(doc.getKbId(), userId);

        // 删除 MinIO 中的文件本体；失败不阻断后续清理（避免残留记录无法删除）
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(doc.getFilePath()).build());
        } catch (Exception ignored) {
        }

        // 同步清理 ChromaDB 中的向量，避免已删文档仍被 RAG 检索召回
        embeddingService.deleteDocument(String.valueOf(doc.getKbId()), String.valueOf(doc.getId()));

        removeById(id);
    }

    @Override
    public List<KbDocument> listByKbId(Long kbId, Long userId) {
        // 先校验知识库归属再列出其下文档
        knowledgeBaseService.getOwned(kbId, userId);
        return list(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, kbId)
                .orderByDesc(KbDocument::getCreateTime));
    }

    /** 确保目标 MinIO bucket 存在，不存在则创建（首次上传时自动初始化存储桶） */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
