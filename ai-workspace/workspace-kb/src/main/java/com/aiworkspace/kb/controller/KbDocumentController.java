package com.aiworkspace.kb.controller;

import com.aiworkspace.common.response.PageResult;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.service.KbDocumentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "知识库文档")
@RestController
@RequestMapping("/api/document")
public class KbDocumentController {

    private final KbDocumentService kbDocumentService;

    public KbDocumentController(KbDocumentService kbDocumentService) {
        this.kbDocumentService = kbDocumentService;
    }

    @Operation(summary = "文档列表（分页）")
    @GetMapping("/list")
    public Result<PageResult<KbDocument>> list(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<KbDocument> p = kbDocumentService.pageByKbId(kbId, page, size);
        return Result.ok(PageResult.of(p));
    }

    @Operation(summary = "上传文档到知识库")
    @PostMapping("/upload")
    public Result<KbDocument> upload(
            @RequestParam Long kbId,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(kbDocumentService.upload(kbId, file));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbDocumentService.delete(id);
        return Result.ok();
    }
}
