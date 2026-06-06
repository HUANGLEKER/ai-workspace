package com.aiworkspace.agent.controller;

import com.aiworkspace.agent.dto.AgentRunRequest;
import com.aiworkspace.agent.entity.Agent;
import com.aiworkspace.agent.service.AgentService;
import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.framework.client.FastApiClient;
import com.aiworkspace.mcp.entity.McpServer;
import com.aiworkspace.mcp.service.McpServerService;
import com.aiworkspace.system.security.LoginUser;
import com.aiworkspace.tool.entity.Tool;
import com.aiworkspace.tool.service.ToolService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Agent")
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final ToolService toolService;
    private final McpServerService mcpServerService;
    private final ObjectMapper objectMapper;
    private final FastApiClient fastApiClient;

    public AgentController(AgentService agentService,
                          ToolService toolService,
                          McpServerService mcpServerService,
                          ObjectMapper objectMapper,
                          FastApiClient fastApiClient) {
        this.agentService = agentService;
        this.toolService = toolService;
        this.mcpServerService = mcpServerService;
        this.objectMapper = objectMapper;
        this.fastApiClient = fastApiClient;
    }

    @Operation(summary = "我的Agent列表")
    @GetMapping("/list")
    public Result<List<Agent>> list() {
        return Result.ok(agentService.listByUser(currentUserId()));
    }

    @Operation(summary = "获取Agent详情")
    @GetMapping("/{id}")
    public Result<Agent> get(@PathVariable Long id) {
        return Result.ok(agentService.getOwned(id, currentUserId()));
    }

    @Operation(summary = "新增Agent")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Agent agent) {
        agentService.create(agent, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "更新Agent")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Agent agent) {
        agentService.update(agent, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除Agent")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "运行Agent")
    @PostMapping("/{id}/run")
    public Result<Map<String, Object>> run(@PathVariable Long id, @RequestBody AgentRunRequest request) {
        Agent agent = agentService.getOwned(id, currentUserId());
        if (Integer.valueOf(0).equals(agent.getEnabled())) {
            throw new BusinessException("该Agent已禁用");
        }
        if (!StringUtils.hasText(request.getInput())) {
            throw new BusinessException("输入内容不能为空");
        }

        Long userId = currentUserId();
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", StringUtils.hasText(request.getSessionId())
                ? request.getSessionId() : "agent-" + id);
        body.put("input", request.getInput());
        body.put("tools", resolveTools(agent.getTools(), userId));
        body.put("mcp_servers", resolveMcpServers(agent.getMcpServers(), userId));
        if (StringUtils.hasText(agent.getSystemPrompt())) {
            body.put("system_prompt", agent.getSystemPrompt());
        }
        if (StringUtils.hasText(agent.getModel())) {
            body.put("model", agent.getModel());
        }

        JsonNode data = fastApiClient.postForData("/agent/run", body);
        Map<String, Object> result = new HashMap<>();
        result.put("output", data.path("output").asText(""));
        result.put("steps", objectMapper.convertValue(data.path("steps"), List.class));
        return Result.ok(result);
    }

    /** Resolve the agent's tool names to enabled HTTP tool specs owned by the caller. */
    private List<Map<String, Object>> resolveTools(String toolNamesJson, Long userId) {
        Set<String> names = parseNames(toolNamesJson);
        List<Map<String, Object>> specs = new ArrayList<>();
        if (names.isEmpty()) {
            return specs;
        }
        for (Tool tool : toolService.listByUser(userId)) {
            if (!names.contains(tool.getName()) || !Integer.valueOf(1).equals(tool.getEnabled())) {
                continue;
            }
            Map<String, Object> spec = new HashMap<>();
            spec.put("name", tool.getName());
            spec.put("description", tool.getDescription() != null ? tool.getDescription() : "");
            spec.put("endpoint", tool.getEndpoint() != null ? tool.getEndpoint() : "");
            spec.put("config", parseObject(tool.getConfig()));
            specs.add(spec);
        }
        return specs;
    }

    /** Resolve the agent's MCP server names to enabled SSE server specs owned by the caller. */
    private List<Map<String, Object>> resolveMcpServers(String serverNamesJson, Long userId) {
        Set<String> names = parseNames(serverNamesJson);
        List<Map<String, Object>> specs = new ArrayList<>();
        if (names.isEmpty()) {
            return specs;
        }
        for (McpServer server : mcpServerService.listByUser(userId)) {
            if (!names.contains(server.getName()) || !Integer.valueOf(1).equals(server.getEnabled())) {
                continue;
            }
            if (!"sse".equalsIgnoreCase(server.getTransport()) || !StringUtils.hasText(server.getUrl())) {
                continue; // only SSE servers can be loaded over HTTP at run time
            }
            Map<String, Object> spec = new HashMap<>();
            spec.put("name", server.getName());
            spec.put("url", server.getUrl());
            spec.put("transport", "sse");
            Object headers = parseObject(server.getConfig()).get("headers");
            if (headers instanceof Map) {
                spec.put("headers", headers);
            }
            specs.add(spec);
        }
        return specs;
    }

    private Set<String> parseNames(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashSet<>();
        }
        try {
            return new LinkedHashSet<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
