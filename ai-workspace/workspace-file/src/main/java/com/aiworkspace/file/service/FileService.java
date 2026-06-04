package com.aiworkspace.file.service;

import com.aiworkspace.file.entity.FileInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

public interface FileService extends IService<FileInfo> {

    FileInfo upload(MultipartFile file, Long userId);

    void delete(Long id, Long userId);

    String getPresignedUrl(String filePath);

    Page<FileInfo> pageList(int page, int size, String fileName);
}
