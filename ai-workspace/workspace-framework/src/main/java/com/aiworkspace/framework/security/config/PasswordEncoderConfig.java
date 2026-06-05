package com.aiworkspace.framework.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Standalone PasswordEncoder configuration, separated from {@link SecurityConfig}
 * to avoid a circular dependency: SecurityConfig depends on JwtAuthFilter, which
 * (transitively) depends on services that need a PasswordEncoder.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
