package com.aiworkspace.kb.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.kb.dto.CreateKbRequest;
import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.aiworkspace.kb.service.KnowledgeBaseService;
import com.aiworkspace.system.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "知识库列表")
    @GetMapping("/list")
    public Result<List<KbKnowledgeBase>> list() {
        return Result.ok(knowledgeBaseService.listByUser(currentUserId()));
    }

    @Operation(summary = "创建知识库")
    @PostMapping("/create")
    public Result<KbKnowledgeBase> create(@RequestBody CreateKbRequest request) {
        KbKnowledgeBase kb = knowledgeBaseService.create(request.getKbName(), request.getDescription(), currentUserId());
        return Result.ok(kb);
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody KbKnowledgeBase kb) {
        knowledgeBaseService.update(kb, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id, currentUserId());
        return Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
