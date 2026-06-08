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

/**
 * 工具中心管理控制器
 *
 * REST 路径前缀：/api/tool
 *
 * 主要职责：用户私有工具的 CRUD（按 createBy 隔离）；
 * 工具由 Agent 运行时通过名称引用，解析为完整 HTTP tool 规格后 POST 给 FastAPI 执行。
 */
@Tag(name = "工具中心")
@RestController
@RequestMapping("/api/tool")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * 查询当前用户的工具列表
     *
     * GET /api/tool/list
     *
     * @return 当前用户拥有的工具列表
     */
    @Operation(summary = "我的工具列表")
    @GetMapping("/list")
    public Result<List<Tool>> list() {
        return Result.ok(toolService.listByUser(currentUserId()));
    }

    /**
     * 获取工具详情
     *
     * GET /api/tool/{id}
     *
     * @param id 工具ID
     * @return 工具详情（已校验归属）
     */
    @Operation(summary = "获取工具详情")
    @GetMapping("/{id}")
    public Result<Tool> get(@PathVariable Long id) {
        return Result.ok(toolService.getOwned(id, currentUserId()));
    }

    /**
     * 新增工具
     *
     * POST /api/tool/add
     *
     * @param tool 待创建的工具
     * @return 操作结果
     */
    @Operation(summary = "新增工具")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Tool tool) {
        toolService.create(tool, currentUserId());
        return Result.ok();
    }

    /**
     * 更新工具
     *
     * PUT /api/tool/update
     *
     * @param tool 待更新的工具
     * @return 操作结果
     */
    @Operation(summary = "更新工具")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Tool tool) {
        toolService.update(tool, currentUserId());
        return Result.ok();
    }

    /**
     * 删除工具
     *
     * DELETE /api/tool/delete/{id}
     *
     * @param id 工具ID
     * @return 操作结果
     */
    @Operation(summary = "删除工具")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolService.delete(id, currentUserId());
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
