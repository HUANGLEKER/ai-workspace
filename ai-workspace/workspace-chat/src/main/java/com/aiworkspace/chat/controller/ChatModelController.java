package com.aiworkspace.chat.controller;

import com.aiworkspace.chat.entity.ChatModel;
import com.aiworkspace.chat.service.ChatModelService;
import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.common.response.PageResult;
import com.aiworkspace.common.response.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型配置控制器
 *
 * REST 路径前缀：/api/chat/model
 *
 * 提供聊天模型的查询与管理。除"获取可用模型列表"供普通用户使用外，
 * 其余增删改查均以 @PreAuthorize("hasRole('ADMIN')") 限定为管理员（RBAC）。
 * apiKey 为敏感字段，由 Service 层脱敏后才返回。
 *
 * @since 2026
 */
@Tag(name = "模型配置")
@RestController
@RequestMapping("/api/chat/model")
public class ChatModelController {

    private final ChatModelService chatModelService;

    public ChatModelController(ChatModelService chatModelService) {
        this.chatModelService = chatModelService;
    }

    /**
     * 获取启用中的可用模型列表（供前端模型选择器，普通用户可访问）
     *
     * GET /api/chat/model/list
     *
     * @return 启用模型列表（apiKey 已脱敏）
     */
    @Operation(summary = "获取可用模型列表")
    @GetMapping("/list")
    public Result<List<ChatModel>> list() {
        return Result.ok(chatModelService.listEnabled());
    }

    /**
     * 模型分页列表（仅管理员）
     *
     * GET /api/chat/model/page
     *
     * @param page      页码，默认 1
     * @param size      每页条数，默认 10
     * @param modelName 模型名称模糊查询条件，可选
     * @return 分页结果（apiKey 已脱敏）
     */
    @Operation(summary = "模型分页列表")
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<ChatModel>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String modelName) {
        Page<ChatModel> p = chatModelService.pageModels(page, size, modelName);
        return Result.ok(PageResult.of(p));
    }

    /**
     * 新增模型（仅管理员）
     *
     * POST /api/chat/model/add
     *
     * @param model 模型配置
     * @return 操作结果
     */
    @Operation(summary = "新增模型")
    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> add(@RequestBody ChatModel model) {
        chatModelService.addModel(model);
        return Result.ok();
    }

    /**
     * 更新模型（仅管理员）
     *
     * PUT /api/chat/model/update
     *
     * @param model 模型配置；apiKey 留空表示保留原密钥
     * @return 操作结果
     */
    @Operation(summary = "更新模型")
    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@RequestBody ChatModel model) {
        chatModelService.updateModel(model);
        return Result.ok();
    }

    /**
     * 删除模型（仅管理员）
     *
     * DELETE /api/chat/model/delete/{id}
     *
     * @param id 模型 ID
     * @return 操作结果
     */
    @Operation(summary = "删除模型")
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        chatModelService.removeById(id);
        return Result.ok();
    }

    /**
     * 启用 / 禁用模型（仅管理员）
     *
     * PUT /api/chat/model/status
     *
     * @param body 含 id 与 enabled 两个字段的请求体
     * @return 操作结果
     * @throws BusinessException id 或 enabled 缺失时抛出
     */
    @Operation(summary = "启用/禁用模型")
    @PutMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> status(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object enabledObj = body.get("enabled");
        if (idObj == null || enabledObj == null) {
            throw new BusinessException("id 和 enabled 不能为空");
        }
        chatModelService.updateStatus(Long.valueOf(idObj.toString()), Integer.valueOf(enabledObj.toString()));
        return Result.ok();
    }
}
