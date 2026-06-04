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

@Tag(name = "文件中心")
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<FileInfo> upload(@RequestParam("file") MultipartFile file) {
        Long userId = currentUserId();
        return Result.ok(fileService.upload(file, userId));
    }

    @Operation(summary = "文件列表")
    @GetMapping("/list")
    public Result<PageResult<FileInfo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String fileName) {
        Page<FileInfo> p = fileService.pageList(page, size, fileName);
        return Result.ok(PageResult.of(p));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = currentUserId();
        fileService.delete(id, userId);
        return Result.ok();
    }

    @Operation(summary = "获取文件预签名URL")
    @GetMapping("/url")
    public Result<String> url(@RequestParam String filePath) {
        return Result.ok(fileService.getPresignedUrl(filePath));
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
