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

/**
 * MCP 服务器管理控制器
 *
 * REST 路径前缀：/api/mcp
 *
 * 主要职责：
 * 1. 用户私有 MCP 服务器的 CRUD（按 createBy 隔离）
 * 2. 提供 sse 传输服务器的 HTTP 连通性检测（POST /api/mcp/test/{id}）
 */
@Tag(name = "MCP服务器")
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    /**
     * 查询当前用户的 MCP 服务器列表
     *
     * GET /api/mcp/list
     *
     * @return 当前用户拥有的 MCP 服务器列表
     */
    @Operation(summary = "我的MCP服务器列表")
    @GetMapping("/list")
    public Result<List<McpServer>> list() {
        return Result.ok(mcpServerService.listByUser(currentUserId()));
    }

    /**
     * 获取 MCP 服务器详情
     *
     * GET /api/mcp/{id}
     *
     * @param id MCP 服务器ID
     * @return MCP 服务器详情（已校验归属）
     */
    @Operation(summary = "获取MCP服务器详情")
    @GetMapping("/{id}")
    public Result<McpServer> get(@PathVariable Long id) {
        return Result.ok(mcpServerService.getOwned(id, currentUserId()));
    }

    /**
     * 新增 MCP 服务器
     *
     * POST /api/mcp/add
     *
     * @param server 待创建的 MCP 服务器
     * @return 操作结果
     */
    @Operation(summary = "新增MCP服务器")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody McpServer server) {
        mcpServerService.create(server, currentUserId());
        return Result.ok();
    }

    /**
     * 更新 MCP 服务器
     *
     * PUT /api/mcp/update
     *
     * @param server 待更新的 MCP 服务器
     * @return 操作结果
     */
    @Operation(summary = "更新MCP服务器")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody McpServer server) {
        mcpServerService.update(server, currentUserId());
        return Result.ok();
    }

    /**
     * 删除 MCP 服务器
     *
     * DELETE /api/mcp/delete/{id}
     *
     * @param id MCP 服务器ID
     * @return 操作结果
     */
    @Operation(summary = "删除MCP服务器")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.delete(id, currentUserId());
        return Result.ok();
    }

    /**
     * 检测 MCP 服务器连通性
     *
     * POST /api/mcp/test/{id}
     *
     * 仅支持 transport=sse 的服务器，对其 URL 发起 HTTP GET 探测，
     * 返回响应码与延迟；可用于前端"测试连接"按钮。
     *
     * @param id MCP 服务器ID
     * @return 连通性描述字符串（如"可达 (HTTP 200, 42ms)"）
     */
    @Operation(summary = "连通性测试")
    @PostMapping("/test/{id}")
    public Result<String> test(@PathVariable Long id) {
        return Result.ok(mcpServerService.testConnectivity(id, currentUserId()));
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
