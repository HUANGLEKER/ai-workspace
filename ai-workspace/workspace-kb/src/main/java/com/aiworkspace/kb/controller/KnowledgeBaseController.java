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

/**
 * 知识库管理 Controller
 *
 * REST 路径前缀：/api/kb
 * 提供知识库的列表/创建/更新/删除能力，全部按当前登录用户做资源归属隔离。
 *
 * @author
 * @since 2026
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 查询当前用户的知识库列表
     *
     * GET /api/kb/list
     *
     * @return 当前用户拥有的知识库列表
     */
    @Operation(summary = "知识库列表")
    @GetMapping("/list")
    public Result<List<KbKnowledgeBase>> list() {
        return Result.ok(knowledgeBaseService.listByUser(currentUserId()));
    }

    /**
     * 创建知识库（归属当前用户）
     *
     * POST /api/kb/create
     *
     * @param request 创建请求（名称、描述）
     * @return 创建后的知识库
     */
    @Operation(summary = "创建知识库")
    @PostMapping("/create")
    public Result<KbKnowledgeBase> create(@RequestBody CreateKbRequest request) {
        KbKnowledgeBase kb = knowledgeBaseService.create(request.getKbName(), request.getDescription(), currentUserId());
        return Result.ok(kb);
    }

    /**
     * 更新知识库（先校验归属）
     *
     * PUT /api/kb/update
     *
     * @param kb 待更新的知识库
     * @return 空结果
     */
    @Operation(summary = "更新知识库")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody KbKnowledgeBase kb) {
        knowledgeBaseService.update(kb, currentUserId());
        return Result.ok();
    }

    /**
     * 删除知识库（先校验归属）
     *
     * DELETE /api/kb/delete/{id}
     *
     * @param id 知识库 ID
     * @return 空结果
     */
    @Operation(summary = "删除知识库")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id, currentUserId());
        return Result.ok();
    }

    /** 从 Spring Security 上下文提取当前登录用户 ID，作为资源归属依据 */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
