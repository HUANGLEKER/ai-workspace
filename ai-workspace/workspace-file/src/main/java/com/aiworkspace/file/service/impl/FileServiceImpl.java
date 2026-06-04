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

@Service
public class FileServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public FileServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public FileInfo upload(MultipartFile file, Long userId) {
        String originalName = file.getOriginalFilename();
        String extension = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
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

    @Override
    public void delete(Long id, Long userId) {
        FileInfo info = getById(id);
        if (info == null) throw new BusinessException("文件不存在");
        if (!userId.equals(info.getUploadBy())) throw new BusinessException("无权操作该文件");
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(info.getFilePath()).build());
        } catch (Exception e) {
            throw new BusinessException("文件删除失败: " + e.getMessage());
        }
        removeById(id);
    }

    @Override
    public String getPresignedUrl(String filePath, Long userId) {
        // verify the caller owns a file record pointing at this object
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
