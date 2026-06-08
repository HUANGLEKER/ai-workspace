package com.aiworkspace.kb.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 *
 * 在 kb 模块内注册 MinioClient Bean，供文档上传/删除使用。
 * 使用 @ConditionalOnMissingBean 避免与其他模块重复注册同一 Bean（按需注入，不冲突）。
 * 配置项来自 application.yml 的 minio.endpoint / minio.access-key / minio.secret-key。
 *
 * @author
 * @since 2026
 */
@Configuration("kbMinioConfig")
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    /**
     * 构建 MinIO 客户端，连接配置的对象存储服务
     *
     * @return 已配置 endpoint 与认证凭证的 MinioClient 实例
     */
    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
