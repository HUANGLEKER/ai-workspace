package com.aiworkspace.framework.security.config;

import com.aiworkspace.framework.security.filter.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置
 *
 * 配置无状态 JWT 鉴权体系的核心安全规则。
 *
 * 主要职责：
 * 1. 定义白名单（登录、Swagger、actuator）放行规则，其余接口一律需认证
 * 2. 关闭 Session（STATELESS）与 CSRF，适配前后端分离 + JWT 的无状态模式
 * 3. 将 JwtAuthFilter 前置于用户名密码过滤器，完成令牌鉴权
 * 4. 配置 CORS 跨域策略与方法级安全（@EnableMethodSecurity 启用 @PreAuthorize）
 *
 * @author
 * @since 2026
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // 免认证白名单：登录接口、Swagger 文档与 actuator 监控端点
    private static final String[] PERMIT_URLS = {
            "/api/auth/login",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/**"
    };

    /**
     * 构建安全过滤器链
     *
     * @param http HttpSecurity 构建器
     * @return 配置完成的安全过滤器链
     * @throws Exception 构建过程中的配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 启用 CORS 跨域支持，规则见 corsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 前后端分离 + JWT 无状态场景下不依赖 Cookie，关闭 CSRF 防护
                .csrf(AbstractHttpConfigurer::disable)
                // 不创建/使用 HttpSession，认证状态完全由 JWT 承载
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_URLS).permitAll()
                        .anyRequest().authenticated())
                // 在用户名密码过滤器之前插入 JWT 过滤器，使携带令牌的请求提前完成认证
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 配置 CORS 跨域规则
     *
     * 设计说明：仅放行前端开发地址（localhost:3000），允许携带凭证（Cookie/Authorization），
     * 并暴露 Authorization 响应头以便前端读取刷新后的令牌；预检结果缓存 1 小时减少 OPTIONS 请求。
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 暴露 AuthenticationManager
     *
     * 设计说明：从 AuthenticationConfiguration 取出容器构建好的认证管理器，
     * 供登录接口（AuthController）执行用户名/密码认证调用。
     *
     * @param config Spring Security 认证配置
     * @return 认证管理器
     * @throws Exception 获取认证管理器失败时抛出
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
