package com.aiworkspace.framework.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置
 *
 * 定义接口文档的基本信息，并声明 Bearer JWT 鉴权方案，
 * 使 Swagger UI 可携带 Authorization 头调试受保护接口。
 *
 * @author
 * @since 2026
 */
@Configuration
public class SwaggerConfig {

    /**
     * 构建 OpenAPI 文档定义
     *
     * 设计说明：注册名为 Bearer 的 HTTP 鉴权方案（bearerFormat=JWT），
     * 并全局加入 SecurityRequirement，使所有接口默认要求携带 JWT 令牌。
     *
     * @return OpenAPI 文档元数据
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Workspace API")
                        .version("1.0.0")
                        .description("AI Workspace 接口文档"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
