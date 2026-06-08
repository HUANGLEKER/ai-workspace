package com.aiworkspace.prompt.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.prompt.entity.Prompt;
import com.aiworkspace.prompt.service.PromptService;
import com.aiworkspace.system.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词中心管理控制器
 *
 * REST 路径前缀：/api/prompt
 *
 * 主要职责：用户私有提示词的 CRUD，支持按标题关键字和分类过滤（按 createBy 隔离）
 */
@Tag(name = "提示词中心")
@RestController
@RequestMapping("/api/prompt")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    /**
     * 查询当前用户的提示词列表，支持关键字和分类过滤
     *
     * GET /api/prompt/list
     *
     * @param keyword  标题模糊搜索关键字（可选）
     * @param category 分类精确匹配（可选）
     * @return 符合条件的提示词列表
     */
    @Operation(summary = "我的提示词列表")
    @GetMapping("/list")
    public Result<List<Prompt>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String category) {
        return Result.ok(promptService.listByUser(currentUserId(), keyword, category));
    }

    /**
     * 获取提示词详情
     *
     * GET /api/prompt/{id}
     *
     * @param id 提示词ID
     * @return 提示词详情（已校验归属）
     */
    @Operation(summary = "获取提示词详情")
    @GetMapping("/{id}")
    public Result<Prompt> get(@PathVariable Long id) {
        return Result.ok(promptService.getOwned(id, currentUserId()));
    }

    /**
     * 新增提示词
     *
     * POST /api/prompt/add
     *
     * @param prompt 待创建的提示词
     * @return 操作结果
     */
    @Operation(summary = "新增提示词")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Prompt prompt) {
        promptService.create(prompt, currentUserId());
        return Result.ok();
    }

    /**
     * 更新提示词
     *
     * PUT /api/prompt/update
     *
     * @param prompt 待更新的提示词
     * @return 操作结果
     */
    @Operation(summary = "更新提示词")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Prompt prompt) {
        promptService.update(prompt, currentUserId());
        return Result.ok();
    }

    /**
     * 删除提示词
     *
     * DELETE /api/prompt/delete/{id}
     *
     * @param id 提示词ID
     * @return 操作结果
     */
    @Operation(summary = "删除提示词")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.delete(id, currentUserId());
        return Result.ok();
    }

    /**
     * 从 Spring Security 上下文取出当前登录用户ID，用于资源归属隔离
     *
     * @return 当前登录用户ID
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
