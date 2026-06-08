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

/**
 * 用户管理 Controller
 *
 * 提供系统用户的增删改查与启停管理，对应 REST 路径前缀 {@code /api/user}。
 *
 * 安全说明：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 锁定整个控制器，
 * 仅管理员（RBAC 角色 ADMIN）可访问；普通用户无权管理账号。
 *
 * @since 2026
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasRole('ADMIN')")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 用户分页列表
     *
     * <p>HTTP: {@code GET /api/user/page}
     *
     * <p>功能：按用户名模糊检索并分页返回用户。响应中已剥离密码哈希。
     *
     * @param page     页码，默认 1
     * @param size     每页条数，默认 10
     * @param username 用户名模糊查询条件，可选
     * @return 用户分页结果
     */
    @Operation(summary = "用户分页列表")
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        Page<SysUser> p = sysUserService.pageUsers(page, size, username);
        return Result.ok(PageResult.of(p));
    }

    /**
     * 新增用户
     *
     * <p>HTTP: {@code POST /api/user/add}
     *
     * <p>功能：创建新账号，密码经 BCrypt 编码后入库。
     *
     * @param user 用户信息（用户名、明文密码等）
     * @return 空响应
     */
    @Operation(summary = "新增用户")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysUser user) {
        sysUserService.addUser(user);
        return Result.ok();
    }

    /**
     * 更新用户
     *
     * <p>HTTP: {@code PUT /api/user/update}
     *
     * <p>功能：更新用户资料。用户名不可变；仅当传入新密码时才重新 BCrypt 哈希。
     *
     * @param user 待更新用户信息（须含 id）
     * @return 空响应
     */
    @Operation(summary = "更新用户")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody SysUser user) {
        sysUserService.updateUser(user);
        return Result.ok();
    }

    /**
     * 删除用户
     *
     * <p>HTTP: {@code DELETE /api/user/delete/{id}}
     *
     * <p>功能：按 id 软删除用户。
     *
     * @param id 用户 id
     * @return 空响应
     */
    @Operation(summary = "删除用户")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.ok();
    }

    /**
     * 启用/禁用用户
     *
     * <p>HTTP: {@code PUT /api/user/status}
     *
     * <p>功能：切换用户状态（0 禁用 / 1 正常）。禁用后该用户登录时会被拒绝。
     *
     * @param body 含 id 与 status 字段的请求体
     * @return 空响应
     * @throws com.aiworkspace.common.exception.BusinessException id 或 status 缺失时抛出
     */
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
