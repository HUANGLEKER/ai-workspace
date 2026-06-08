package com.aiworkspace.framework.client;

import com.aiworkspace.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * FastAPI AI 服务统一调用客户端
 *
 * <p>所有对 FastAPI（Chat / RAG / Agent / Workflow / Embedding）的调用均收敛到此处，
 * 替代此前散落在各模块、按调用各自 new 的 {@code HttpURLConnection}。
 *
 * <p>主要职责：
 * 1. 基于 JDK {@link HttpClient} 复用底层连接池，避免每次调用重建 TCP 连接
 * 2. 集中管理超时（连接 / 读取 / 流式分级）
 * 3. 统一解包 {@code {code,message,data}} 响应协议并转换为 {@link BusinessException}
 *
 * <p>作为单例 Spring Bean 注入，线程安全（{@code HttpClient} 本身可并发复用）。
 */
@Component
public class FastApiClient {

    private static final Logger log = LoggerFactory.getLogger(FastApiClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FastApiProperties props;

    public FastApiClient(ObjectMapper objectMapper, FastApiProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
        // 构建全局共享的 HttpClient：连接池由其内部维护，超时来自 FastApiProperties 配置。
        // 强制使用 HTTP/1.1，规避部分场景下 HTTP/2 对 SSE 长流的兼容性问题。
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeout()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 发送一元 JSON POST 请求，并解包返回标准响应中的 {@code data} 节点
     *
     * <p>适用于 agent / workflow 等需要完整读取响应体的同步调用。
     *
     * @param path FastAPI 相对路径（如 {@code /agent/run}）
     * @param body 请求体对象，序列化为 JSON
     * @return 响应协议中的 {@code data} 节点
     * @throws BusinessException 传输失败，或响应 HTTP 非 2xx / 业务 code 非 200 时抛出
     */
    public JsonNode postForData(String path, Object body) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    jsonRequest("POST", path, body, props.getReadTimeout()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            int code = resp.statusCode();
            // 同时校验 HTTP 状态码与响应体内的业务 code，二者任一异常都视为调用失败
            if (code >= 400 || (root.has("code") && root.get("code").asInt() != 200)) {
                throw new BusinessException("AI服务调用失败: " + root.path("message").asText("HTTP " + code));
            }
            return root.path("data");
        } catch (BusinessException e) {
            // 业务异常直接透传，避免被下方兜底分支二次包装
            throw e;
        } catch (Exception e) {
            // 传输层异常（连接超时、网络不可达等）统一转为对调用方友好的业务异常
            log.warn("FastAPI call {} failed: {}", path, e.getMessage());
            throw new BusinessException("AI服务不可用: " + e.getMessage());
        }
    }

    /**
     * 发送 JSON 请求并仅校验 2xx 状态，不读取响应体
     *
     * <p>用于"发起即确认"类调用（如嵌入构建 / 删除），调用方只关心是否成功。
     *
     * @param method        HTTP 方法（POST / DELETE 等）
     * @param path          FastAPI 相对路径
     * @param body          请求体对象
     * @param readTimeoutMs 本次调用的读取超时（毫秒），覆盖默认值——嵌入构建耗时较长，需单独放宽
     * @throws Exception 网络异常或响应 HTTP 非 2xx 时抛出
     */
    public void send(String method, String path, Object body, int readTimeoutMs) throws Exception {
        HttpResponse<Void> resp = httpClient.send(
                jsonRequest(method, path, body, readTimeoutMs),
                HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("FastAPI " + path + " failed, HTTP " + resp.statusCode());
        }
    }

    /**
     * 按行流式读取 JSON POST 响应（SSE 代理）
     *
     * <p>用于 Chat / RAG 流式场景：将 FastAPI 的 SSE 逐行透传给浏览器。
     * 每一行原始文本交由 {@code lineConsumer} 处理；当消费者抛出异常时
     * （典型为浏览器断连），循环立即中止，并借助 try-with-resources 释放上游连接，
     * 避免无效的 LLM 推理继续占用连接与算力。
     *
     * @param path         FastAPI 相对路径（如 {@code /chat}）
     * @param body         请求体对象
     * @param lineConsumer 逐行回调，负责写回客户端
     * @throws Exception 建立连接或读取流失败时抛出
     */
    public void stream(String path, Object body, Consumer<String> lineConsumer) throws Exception {
        // 流式调用使用独立的 streamTimeout（远大于普通读取超时），适配长连接 SSE
        HttpResponse<Stream<String>> resp = httpClient.send(
                jsonRequest("POST", path, body, props.getStreamTimeout()),
                HttpResponse.BodyHandlers.ofLines());
        // try-with-resources 确保流（及底层连接）在断连或正常结束后均被释放
        try (Stream<String> lines = resp.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                lineConsumer.accept(it.next());
            }
        }
    }

    /**
     * 构建统一的 JSON HTTP 请求
     *
     * <p>集中设置 base URL 拼接、超时、Content-Type 与序列化后的请求体，
     * 供上述三种调用方式（一元 / 确认 / 流式）复用。
     */
    private HttpRequest jsonRequest(String method, String path, Object body, int timeoutMs) throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(body);
        return HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
    }
}
