package com.aiworkspace.system.controller;

import com.aiworkspace.common.response.PageResult;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.system.entity.SysUser;
import com.aiworkspace.system.service.SysUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasRole('ADMIN')")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "用户分页列表")
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        Page<SysUser> p = sysUserService.pageUsers(page, size, username);
        return Result.ok(PageResult.of(p));
    }

    @Operation(summary = "新增用户")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysUser user) {
        sysUserService.addUser(user);
        return Result.ok();
    }

    @Operation(summary = "更新用户")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody SysUser user) {
        sysUserService.updateUser(user);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/status")
    public Result<Void> status(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object statusObj = body.get("status");
        if (idObj == null || statusObj == null) {
            throw new com.aiworkspace.common.exception.BusinessException("id 和 status 不能为空");
        }
        sysUserService.updateStatus(Long.valueOf(idObj.toString()), Integer.valueOf(statusObj.toString()));
        return Result.ok();
    }
}
