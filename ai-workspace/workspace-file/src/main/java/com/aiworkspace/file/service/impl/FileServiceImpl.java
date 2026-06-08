package com.aiworkspace.file.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.file.entity.FileInfo;
import com.aiworkspace.file.mapper.FileInfoMapper;
import com.aiworkspace.file.service.FileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务实现
 *
 * 集成 MinIO 对象存储完成文件上传/删除/预签名 URL，
 * 并在所有读写路径上按 uploadBy 校验归属，防止越权访问（IDOR）。
 */
@Service
public class FileServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public FileServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 上传文件至 MinIO 并保存元数据
     *
     * @param file   上传的文件
     * @param userId 上传人用户 ID
     * @return 保存后的文件信息
     * @throws BusinessException 上传到 MinIO 失败时抛出
     */
    @Override
    public FileInfo upload(MultipartFile file, Long userId) {
        String originalName = file.getOriginalFilename();
        String extension = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        // 用 UUID 生成存储名，避免不同用户/重名文件在对象存储中相互覆盖
        String storedName = UUID.randomUUID() + extension;
        String filePath = "files/" + storedName;

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

        FileInfo info = new FileInfo();
        info.setFileName(originalName);
        info.setFilePath(filePath);
        info.setFileSize(file.getSize());
        info.setFileType(file.getContentType());
        info.setUploadBy(userId);
        save(info);
        return info;
    }

    /**
     * 删除文件
     *
     * 先校验文件归属：userId 必须匹配 uploadBy，防止越权删除（IDOR）。
     * 先从 MinIO 删除对象，再软删除元数据行，保持一致性。
     *
     * @param id     文件 ID
     * @param userId 当前用户 ID
     * @throws BusinessException 文件不存在、无权操作或 MinIO 删除失败时抛出
     */
    @Override
    public void delete(Long id, Long userId) {
        FileInfo info = getById(id);
        if (info == null) throw new BusinessException("文件不存在");
        // 归属校验：只有上传人才能删除自己的文件
        if (!userId.equals(info.getUploadBy())) throw new BusinessException("无权操作该文件");
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(info.getFilePath()).build());
        } catch (Exception e) {
            throw new BusinessException("文件删除失败: " + e.getMessage());
        }
        removeById(id);
    }

    /**
     * 获取文件预签名（presign）访问 URL
     *
     * presign 机制：MinIO 对指定对象生成一个携带签名的临时 URL，
     * 任何持有该 URL 的人均可在有效期内直接下载，无需额外鉴权。
     * 为防止越权（IDOR），生成前必须按 filePath + uploadBy 双重条件确认调用方拥有该文件，
     * 否则攻击者可通过猜测或枚举 filePath 获取他人文件的下载链接。
     *
     * @param filePath MinIO 对象路径
     * @param userId   当前用户 ID，用于归属校验
     * @return 有效期 1 小时的临时预签名 URL
     * @throws BusinessException 文件不存在/无权访问或生成失败时抛出
     */
    @Override
    public String getPresignedUrl(String filePath, Long userId) {
        // 同时校验 filePath 与 uploadBy，杜绝通过路径枚举访问他人文件
        FileInfo info = lambdaQuery()
                .eq(FileInfo::getFilePath, filePath)
                .eq(FileInfo::getUploadBy, userId)
                .one();
        if (info == null) throw new BusinessException("文件不存在或无权访问");
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("获取文件URL失败: " + e.getMessage());
        }
    }

    @Override
    public Page<FileInfo> pageList(int page, int size, String fileName, Long userId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUploadBy, userId)
                .orderByDesc(FileInfo::getCreateTime);
        if (StringUtils.hasText(fileName)) {
            wrapper.like(FileInfo::getFileName, fileName);
        }
        return page(new Page<>(page, size), wrapper);
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
