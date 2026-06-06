package com.aiworkspace.framework.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized configuration for the FastAPI AI service connection.
 * Replaces the {@code @Value("${fastapi.base-url}")} field injection that was
 * duplicated across every controller/service talking to the AI service.
 */
@Component
@ConfigurationProperties(prefix = "fastapi")
public class FastApiProperties {

    /** Base URL of the FastAPI service, e.g. {@code http://localhost:8001}. */
    private String baseUrl = "http://localhost:8001";

    /** TCP connect timeout in milliseconds. */
    private int connectTimeout = 10_000;

    /** Default request/read timeout in milliseconds for unary JSON calls. */
    private int readTimeout = 180_000;

    /** Request timeout in milliseconds for streaming (SSE) calls. */
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
