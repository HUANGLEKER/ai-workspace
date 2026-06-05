package com.aiworkspace.tool.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.system.security.LoginUser;
import com.aiworkspace.tool.entity.Tool;
import com.aiworkspace.tool.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工具中心")
@RestController
@RequestMapping("/api/tool")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @Operation(summary = "我的工具列表")
    @GetMapping("/list")
    public Result<List<Tool>> list() {
        return Result.ok(toolService.listByUser(currentUserId()));
    }

    @Operation(summary = "获取工具详情")
    @GetMapping("/{id}")
    public Result<Tool> get(@PathVariable Long id) {
        return Result.ok(toolService.getOwned(id, currentUserId()));
    }

    @Operation(summary = "新增工具")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Tool tool) {
        toolService.create(tool, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "更新工具")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Tool tool) {
        toolService.update(tool, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除工具")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolService.delete(id, currentUserId());
        return Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
