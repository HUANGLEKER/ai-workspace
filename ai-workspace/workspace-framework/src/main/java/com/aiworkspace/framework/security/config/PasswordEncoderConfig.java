package com.aiworkspace.framework.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置
 *
 * 独立于 {@link SecurityConfig} 单独配置 PasswordEncoder，
 * 以打破循环依赖：SecurityConfig 依赖 JwtAuthFilter，而后者（间接）依赖
 * 需要 PasswordEncoder 的业务服务；拆出该 Bean 可避免 Bean 初始化环。
 *
 * @author
 * @since 2026
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 提供全局密码编码器
     *
     * 设计说明：采用 BCrypt，内置随机盐且为自适应慢哈希，可有效抵御彩虹表与暴力破解，
     * 用户密码仅以哈希形式持久化，登录时通过 matches 比对。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
