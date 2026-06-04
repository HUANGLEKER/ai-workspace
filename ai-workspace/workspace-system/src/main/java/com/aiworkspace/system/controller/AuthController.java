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

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        String token = jwtUtil.generateToken(loginUser.getUsername());
        return Result.ok(new LoginResponse(token));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityContextHolder.clearContext();
        return Result.ok();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<SysUser> info() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUser user = sysUserService.getById(loginUser.getSysUser().getId());
        user.setPassword(null);
        return Result.ok(user);
    }
}
