package com.aiworkspace.system.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.framework.security.util.JwtUtil;
import com.aiworkspace.system.dto.LoginRequest;
import com.aiworkspace.system.dto.LoginResponse;
import com.aiworkspace.system.entity.SysUser;
import com.aiworkspace.system.security.LoginUser;
import com.aiworkspace.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证管理 Controller
 *
 * 负责平台的登录认证与当前用户信息查询，对应 REST 路径前缀 {@code /api/auth}。
 *
 * 主要职责：
 * 1. 用户名/密码登录，签发 JWT 令牌
 * 2. 登出（清理安全上下文）
 * 3. 返回当前登录用户的基础信息与角色（供前端 RBAC 菜单/路由守卫使用）
 *
 * 设计说明：登录由 Spring Security 的 {@link AuthenticationManager} 驱动，
 * 完成认证后改用无状态 JWT 维持会话，后续请求由 JwtAuthFilter 校验令牌。
 *
 * @since 2026
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final SysUserService sysUserService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, SysUserService sysUserService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.sysUserService = sysUserService;
    }

    /**
     * 用户登录
     *
     * <p>HTTP: {@code POST /api/auth/login}
     *
     * <p>功能：校验用户名/密码，认证通过后签发 JWT 令牌返回前端。
     *
     * @param request 登录请求（用户名、密码，已做非空校验）
     * @return 包含 JWT token 的登录响应
     * @throws org.springframework.security.core.AuthenticationException 凭证无效或账号被禁用时抛出
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        // 交由 Spring Security 完成密码 BCrypt 比对与账号状态校验，认证失败会抛异常
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        // 认证成功后签发无状态 JWT，替代服务端 session 维持登录态
        String token = jwtUtil.generateToken(loginUser.getUsername());
        return Result.ok(new LoginResponse(token));
    }

    /**
     * 用户登出
     *
     * <p>HTTP: {@code POST /api/auth/logout}
     *
     * <p>功能：清理当前线程的安全上下文。JWT 为无状态令牌，真正失效依赖前端丢弃令牌。
     *
     * @return 空响应
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityContextHolder.clearContext();
        return Result.ok();
    }

    /**
     * 获取当前登录用户信息
     *
     * <p>HTTP: {@code GET /api/auth/info}
     *
     * <p>功能：返回当前用户的基础资料及其角色列表（roles），前端据此判定 isAdmin 并做菜单/路由 RBAC 控制。
     *
     * @return 用户信息及角色集合
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        // 从安全上下文取出已认证主体，重新查库获取最新资料
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUser user = sysUserService.getById(loginUser.getSysUser().getId());
        // 安全要求：绝不向前端返回密码哈希，置空后再组装响应
        user.setPassword(null);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("status", user.getStatus());
        data.put("createTime", user.getCreateTime());
        data.put("roles", loginUser.getRoles());
        return Result.ok(data);
    }
}
