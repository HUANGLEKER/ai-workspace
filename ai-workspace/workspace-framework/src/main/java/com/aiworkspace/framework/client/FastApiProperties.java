package com.aiworkspace.framework.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FastAPI AI 服务连接配置
 *
 * 集中管理 Spring Boot 调用 FastAPI 的连接参数（基址与各类超时），
 * 取代此前散落在各 controller/service 中重复的 {@code @Value("${fastapi.base-url}")} 注入，
 * 由统一的 FastApiClient 复用，便于集中维护连接池与超时策略。
 *
 * 配置前缀：{@code fastapi.*}（见 application.yml）
 *
 * @author
 * @since 2026
 */
@Component
@ConfigurationProperties(prefix = "fastapi")
public class FastApiProperties {

    /** FastAPI 服务基址，例如 {@code http://localhost:8001} */
    private String baseUrl = "http://localhost:8001";

    /** TCP 连接建立超时（毫秒） */
    private int connectTimeout = 10_000;

    /** 一元 JSON 调用（agent/workflow 等）的请求/读取超时（毫秒），因 LLM 推理较慢故设置较长 */
    private int readTimeout = 180_000;

    /** 流式（SSE）调用（chat/rag）的请求超时（毫秒），需覆盖整条流的生命周期 */
    private int streamTimeout = 180_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getStreamTimeout() {
        return streamTimeout;
    }

    public void setStreamTimeout(int streamTimeout) {
        this.streamTimeout = streamTimeout;
    }
}
