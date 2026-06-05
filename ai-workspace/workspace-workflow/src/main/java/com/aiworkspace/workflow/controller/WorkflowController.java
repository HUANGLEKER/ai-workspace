package com.aiworkspace.workflow.controller;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.system.security.LoginUser;
import com.aiworkspace.workflow.dto.WorkflowRunRequest;
import com.aiworkspace.workflow.entity.Workflow;
import com.aiworkspace.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "工作流")
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    @Value("${fastapi.base-url}")
    private String fastapiBaseUrl;

    public WorkflowController(WorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "我的工作流列表")
    @GetMapping("/list")
    public Result<List<Workflow>> list() {
        return Result.ok(workflowService.listByUser(currentUserId()));
    }

    @Operation(summary = "获取工作流详情")
    @GetMapping("/{id}")
    public Result<Workflow> get(@PathVariable Long id) {
        return Result.ok(workflowService.getOwned(id, currentUserId()));
    }

    @Operation(summary = "新增工作流")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Workflow workflow) {
        workflowService.create(workflow, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Workflow workflow) {
        workflowService.update(workflow, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除工作流")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "运行工作流")
    @PostMapping("/{id}/run")
    public Result<Map<String, Object>> run(@PathVariable Long id, @RequestBody WorkflowRunRequest request) {
        Workflow workflow = workflowService.getOwned(id, currentUserId());
        if (Integer.valueOf(0).equals(workflow.getEnabled())) {
            throw new BusinessException("该工作流已禁用");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("workflow_id", String.valueOf(id));
        body.put("session_id", StringUtils.hasText(request.getSessionId())
                ? request.getSessionId() : "workflow-" + id);
        body.put("inputs", request.getInputs() != null ? request.getInputs() : new HashMap<>());
        if (StringUtils.hasText(workflow.getModel())) {
            body.put("model", workflow.getModel());
        }

        JsonNode data = postFastapi("/workflow/run", body);
        Map<String, Object> result = new HashMap<>();
        result.put("status", data.path("status").asText("failed"));
        result.put("outputs", objectMapper.convertValue(data.path("outputs"), Map.class));
        return Result.ok(result);
    }

    /** POST a JSON body to the FastAPI service and return the unwrapped {@code data} node. */
    private JsonNode postFastapi(String path, Map<String, Object> body) {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(fastapiBaseUrl + path);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(180_000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(body));
            }
            int code = conn.getResponseCode();
            try (InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                if (code >= 400 || (root.has("code") && root.get("code").asInt() != 200)) {
                    throw new BusinessException("AI服务调用失败: " + root.path("message").asText("HTTP " + code));
                }
                return root.path("data");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI服务不可用: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
