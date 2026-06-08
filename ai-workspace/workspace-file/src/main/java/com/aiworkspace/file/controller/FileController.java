package com.aiworkspace.file.controller;

import com.aiworkspace.common.response.PageResult;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.file.entity.FileInfo;
import com.aiworkspace.file.service.FileService;
import com.aiworkspace.system.security.LoginUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件中心 Controller
 *
 * REST 路径前缀：/api/file
 *
 * 提供基于 MinIO 的文件上传、列表查询、删除与预签名 URL 获取。
 * 所有接口均要求登录，并按当前用户的 uploadBy 隔离文件归属，
 * 防止越权访问他人文件（IDOR）。
 */
@Tag(name = "文件中心")
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传文件至 MinIO 并记录元数据
     *
     * POST /api/file/upload
     *
     * @param file 上传的文件（multipart/form-data）
     * @return 保存后的文件信息（含 MinIO 对象路径）
     */
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<FileInfo> upload(@RequestParam("file") MultipartFile file) {
        Long userId = currentUserId();
        return Result.ok(fileService.upload(file, userId));
    }

    /**
     * 分页查询当前用户的文件列表
     *
     * GET /api/file/list
     *
     * 按 uploadBy 隔离，只返回调用方自己上传的文件，防止越权读取（IDOR）。
     *
     * @param page     页码，默认 1
     * @param size     每页条数，默认 10
     * @param fileName 文件名模糊过滤，可选
     * @return 文件分页结果
     */
    @Operation(summary = "文件列表")
    @GetMapping("/list")
    public Result<PageResult<FileInfo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String fileName) {
        Page<FileInfo> p = fileService.pageList(page, size, fileName, currentUserId());
        return Result.ok(PageResult.of(p));
    }

    /**
     * 删除文件（校验归属后同时移除 MinIO 对象与元数据）
     *
     * DELETE /api/file/delete/{id}
     *
     * @param id 文件 ID
     * @return 操作结果
     */
    @Operation(summary = "删除文件")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = currentUserId();
        fileService.delete(id, userId);
        return Result.ok();
    }

    /**
     * 获取文件临时预签名（presign）下载 URL
     *
     * GET /api/file/url?filePath=...
     *
     * presign URL 有时效（1 小时），任何持有 URL 的人均可直接下载，无需额外鉴权。
     * 服务层按 filePath + uploadBy 双重校验，防止通过路径枚举获取他人文件链接。
     *
     * @param filePath MinIO 对象路径
     * @return 有时效的预签名 URL
     */
    @Operation(summary = "获取文件预签名URL")
    @GetMapping("/url")
    public Result<String> url(@RequestParam String filePath) {
        return Result.ok(fileService.getPresignedUrl(filePath, currentUserId()));
    }

    /**
     * 从 Spring Security 上下文获取当前登录用户 ID
     *
     * @return 当前用户 ID
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
