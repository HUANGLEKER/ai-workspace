package com.aiworkspace.mcp.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.mcp.entity.McpServer;
import com.aiworkspace.mcp.service.McpServerService;
import com.aiworkspace.system.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "MCP服务器")
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @Operation(summary = "我的MCP服务器列表")
    @GetMapping("/list")
    public Result<List<McpServer>> list() {
        return Result.ok(mcpServerService.listByUser(currentUserId()));
    }

    @Operation(summary = "获取MCP服务器详情")
    @GetMapping("/{id}")
    public Result<McpServer> get(@PathVariable Long id) {
        return Result.ok(mcpServerService.getOwned(id, currentUserId()));
    }

    @Operation(summary = "新增MCP服务器")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody McpServer server) {
        mcpServerService.create(server, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "更新MCP服务器")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody McpServer server) {
        mcpServerService.update(server, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除MCP服务器")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.delete(id, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "连通性测试")
    @PostMapping("/test/{id}")
    public Result<String> test(@PathVariable Long id) {
        return Result.ok(mcpServerService.testConnectivity(id, currentUserId()));
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
