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

@Tag(name = "模型配置")
@RestController
@RequestMapping("/api/chat/model")
public class ChatModelController {

    private final ChatModelService chatModelService;

    public ChatModelController(ChatModelService chatModelService) {
        this.chatModelService = chatModelService;
    }

    @Operation(summary = "获取可用模型列表")
    @GetMapping("/list")
    public Result<List<ChatModel>> list() {
        return Result.ok(chatModelService.listEnabled());
    }

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

    @Operation(summary = "新增模型")
    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> add(@RequestBody ChatModel model) {
        chatModelService.addModel(model);
        return Result.ok();
    }

    @Operation(summary = "更新模型")
    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@RequestBody ChatModel model) {
        chatModelService.updateModel(model);
        return Result.ok();
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        chatModelService.removeById(id);
        return Result.ok();
    }

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
