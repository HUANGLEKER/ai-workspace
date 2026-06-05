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

@Tag(name = "提示词中心")
@RestController
@RequestMapping("/api/prompt")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @Operation(summary = "我的提示词列表")
    @GetMapping("/list")
    public Result<List<Prompt>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String category) {
        return Result.ok(promptService.listByUser(currentUserId(), keyword, category));
    }

    @Operation(summary = "获取提示词详情")
    @GetMapping("/{id}")
    public Result<Prompt> get(@PathVariable Long id) {
        return Result.ok(promptService.getOwned(id, currentUserId()));
    }

    @Operation(summary = "新增提示词")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Prompt prompt) {
        promptService.create(prompt, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "更新提示词")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Prompt prompt) {
        promptService.update(prompt, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除提示词")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.delete(id, currentUserId());
        return Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
