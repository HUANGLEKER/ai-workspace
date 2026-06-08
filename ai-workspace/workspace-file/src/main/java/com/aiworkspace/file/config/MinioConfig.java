package com.aiworkspace.file.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储客户端配置
 *
 * 从 application.yml 的 minio.* 属性读取连接参数，构建并注册 MinioClient bean。
 * 使用 @ConditionalOnMissingBean 保证多模块场景下只注册一个 MinioClient 实例，
 * 避免与其他模块的 MinIO 配置冲突。
 */
@Configuration("fileMinioConfig")
public class MinioConfig {

    /** MinIO 服务地址（如 http://localhost:9000） */
    @Value("${minio.endpoint}")
    private String endpoint;

    /** MinIO 访问密钥 ID（Access Key） */
    @Value("${minio.access-key}")
    private String accessKey;

    /** MinIO 访问密钥 Secret */
    @Value("${minio.secret-key}")
    private String secretKey;

    /** 是否启用 HTTPS，本地开发默认关闭 */
    @Value("${minio.secure:false}")
    private boolean secure;

    /**
     * 构建 MinioClient bean
     *
     * 仅当容器中尚无 MinioClient 实例时才注册，防止多模块重复定义时产生 bean 冲突。
     *
     * @return 已配置连接参数的 MinioClient 实例
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
