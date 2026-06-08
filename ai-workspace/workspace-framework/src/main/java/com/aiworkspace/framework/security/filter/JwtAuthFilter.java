package com.aiworkspace.framework.security.filter;

import com.aiworkspace.common.constant.CommonConstants;
import com.aiworkspace.framework.security.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * 继承 OncePerRequestFilter 保证每次请求仅执行一次，置于 Spring Security 过滤器链中，
 * 负责从请求头提取 JWT 并据此建立安全上下文，实现无状态（STATELESS）鉴权。
 *
 * 主要职责：
 * 1. 从 Authorization 头解析 Bearer 令牌
 * 2. 校验令牌有效性并加载用户详情与权限
 * 3. 将认证信息写入 SecurityContext，供后续 RBAC 鉴权使用
 *
 * @author
 * @since 2026
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        // 仅当令牌存在且通过签名/有效期校验时才尝试建立认证；否则放行交由后续授权环节拒绝
        if (StringUtils.hasText(token) && jwtUtil.isTokenValid(token)) {
            String username = jwtUtil.extractUsername(token);
            // 上下文已有认证信息则跳过，避免重复加载用户与权限
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // 加载用户详情（含从角色码解析出的权限），用于后续 @PreAuthorize RBAC 判定
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 无论认证是否成功都继续过滤器链，未认证请求由后续授权配置统一拦截
        filterChain.doFilter(request, response);
    }

    // 从 Authorization 头中剥离 "Bearer " 前缀，提取原始 JWT；格式不符返回 null
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(CommonConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
