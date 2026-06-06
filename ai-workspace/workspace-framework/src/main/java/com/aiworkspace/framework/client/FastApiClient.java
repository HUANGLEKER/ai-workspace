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
 * Single shared client for all calls to the FastAPI AI service.
 *
 * <p>Built on the JDK {@link HttpClient}, which pools and reuses connections
 * internally — replacing the per-call {@code HttpURLConnection} that was
 * copy-pasted across Chat / RAG / Agent / Workflow / Embedding. Centralizes
 * timeouts, error unwrapping and the standardized {@code {code,message,data}}
 * response protocol in one place.
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
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeout()))
                .build();
    }

    /**
     * POST a JSON body and return the unwrapped {@code data} node.
     * Throws {@link BusinessException} on transport failure or a non-200 envelope.
     */
    public JsonNode postForData(String path, Object body) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    jsonRequest("POST", path, body, props.getReadTimeout()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            int code = resp.statusCode();
            if (code >= 400 || (root.has("code") && root.get("code").asInt() != 200)) {
                throw new BusinessException("AI服务调用失败: " + root.path("message").asText("HTTP " + code));
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("FastAPI call {} failed: {}", path, e.getMessage());
            throw new BusinessException("AI服务不可用: " + e.getMessage());
        }
    }

    /**
     * Send a JSON request and require a 2xx status. Used for fire-and-confirm
     * calls (embedding build/delete) where no response body is consumed.
     *
     * @param readTimeoutMs per-call read timeout override in milliseconds
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
     * Stream a JSON POST response line by line (SSE proxy). Each raw line is
     * passed to {@code lineConsumer}; if the consumer throws (e.g. the browser
     * disconnected), streaming stops and the upstream connection is released.
     */
    public void stream(String path, Object body, Consumer<String> lineConsumer) throws Exception {
        HttpResponse<Stream<String>> resp = httpClient.send(
                jsonRequest("POST", path, body, props.getStreamTimeout()),
                HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = resp.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                lineConsumer.accept(it.next());
            }
        }
    }

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
