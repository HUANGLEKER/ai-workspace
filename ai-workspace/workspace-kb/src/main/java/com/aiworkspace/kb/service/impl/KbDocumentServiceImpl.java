package com.aiworkspace.kb.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.mapper.KbDocumentMapper;
import com.aiworkspace.kb.service.EmbeddingService;
import com.aiworkspace.kb.service.KbDocumentService;
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

@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
        implements KbDocumentService {

    private final MinioClient minioClient;
    private final EmbeddingService embeddingService;

    @Value("${minio.bucket}")
    private String bucket;

    public KbDocumentServiceImpl(MinioClient minioClient, EmbeddingService embeddingService) {
        this.minioClient = minioClient;
        this.embeddingService = embeddingService;
    }

    @Override
    public Page<KbDocument> pageByKbId(Long kbId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .orderByDesc(KbDocument::getCreateTime));
    }

    @Override
    public KbDocument upload(Long kbId, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase()
                : "";
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
        doc.setStatus("PENDING");
        save(doc);

        // trigger async embedding
        embeddingService.buildAsync(doc);

        return doc;
    }

    @Override
    public void delete(Long id) {
        KbDocument doc = getById(id);
        if (doc == null) throw new BusinessException("文档不存在");

        // remove from MinIO
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(doc.getFilePath()).build());
        } catch (Exception ignored) {
        }

        // remove vector data
        embeddingService.deleteDocument(String.valueOf(doc.getKbId()), String.valueOf(doc.getId()));

        removeById(id);
    }

    @Override
    public List<KbDocument> listByKbId(Long kbId) {
        return list(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, kbId)
                .orderByDesc(KbDocument::getCreateTime));
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
