package com.aiworkspace.file.service;

import com.aiworkspace.file.entity.FileInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 *
 * 定义基于 MinIO 的文件上传、删除、预签名 URL 获取与分页查询能力，
 * 所有操作均按 uploadBy 进行用户归属隔离。
 */
public interface FileService extends IService<FileInfo> {

    /**
     * 上传文件至 MinIO 并记录元数据
     *
     * @param file   上传的文件
     * @param userId 上传人用户 ID
     * @return 保存后的文件信息
     * @throws com.aiworkspace.common.exception.BusinessException 上传失败时抛出
     */
    FileInfo upload(MultipartFile file, Long userId);

    /**
     * 删除文件（校验归属后同时移除 MinIO 对象与元数据）
     *
     * @param id     文件 ID
     * @param userId 当前用户 ID，用于归属校验
     * @throws com.aiworkspace.common.exception.BusinessException 文件不存在、无权操作或删除失败时抛出
     */
    void delete(Long id, Long userId);

    /**
     * 获取文件的临时预签名（presign）访问 URL
     *
     * @param filePath MinIO 对象路径
     * @param userId   当前用户 ID，用于归属校验
     * @return 有时效的预签名 URL
     * @throws com.aiworkspace.common.exception.BusinessException 文件不存在/无权访问或生成失败时抛出
     */
    String getPresignedUrl(String filePath, Long userId);

    /**
     * 分页查询当前用户的文件列表
     *
     * @param page     页码
     * @param size     每页条数
     * @param fileName 文件名模糊过滤，可为空
     * @param userId   当前用户 ID，按 uploadBy 隔离
     * @return 文件分页结果
     */
    Page<FileInfo> pageList(int page, int size, String fileName, Long userId);
}
