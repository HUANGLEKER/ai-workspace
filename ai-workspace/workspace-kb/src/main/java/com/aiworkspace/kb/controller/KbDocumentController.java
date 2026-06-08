package com.aiworkspace.kb.controller;

import com.aiworkspace.common.response.PageResult;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.service.KbDocumentService;
import com.aiworkspace.system.security.LoginUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档 Controller
 *
 * REST 路径前缀：/api/document
 * 提供文档的分页列表/上传/删除能力，均按所属知识库的归属做用户隔离。
 * 上传会触发异步 embedding 向量化；删除会同步清理 MinIO 文件与 ChromaDB 向量。
 *
 * @author
 * @since 2026
 */
@Tag(name = "知识库文档")
@RestController
@RequestMapping("/api/document")
public class KbDocumentController {

    private final KbDocumentService kbDocumentService;

    public KbDocumentController(KbDocumentService kbDocumentService) {
        this.kbDocumentService = kbDocumentService;
    }

    /**
     * 分页查询指定知识库下的文档
     *
     * GET /api/document/list
     *
     * @param kbId 知识库 ID
     * @param page 页码（默认 1）
     * @param size 每页条数（默认 10）
     * @return 文档分页结果
     */
    @Operation(summary = "文档列表（分页）")
    @GetMapping("/list")
    public Result<PageResult<KbDocument>> list(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<KbDocument> p = kbDocumentService.pageByKbId(kbId, page, size, currentUserId());
        return Result.ok(PageResult.of(p));
    }

    /**
     * 上传文档到知识库（存 MinIO + 触发异步向量化）
     *
     * POST /api/document/upload
     *
     * @param kbId 目标知识库 ID
     * @param file 上传的文件
     * @return 落库后的文档记录（初始状态 PENDING）
     */
    @Operation(summary = "上传文档到知识库")
    @PostMapping("/upload")
    public Result<KbDocument> upload(
            @RequestParam Long kbId,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(kbDocumentService.upload(kbId, file, currentUserId()));
    }

    /**
     * 删除文档（同步清理 MinIO 与向量数据）
     *
     * DELETE /api/document/delete/{id}
     *
     * @param id 文档 ID
     * @return 空结果
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbDocumentService.delete(id, currentUserId());
        return Result.ok();
    }

    /** 从 Spring Security 上下文提取当前登录用户 ID，作为资源归属依据 */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
