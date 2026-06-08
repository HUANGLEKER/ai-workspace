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

/**
 * Agent 管理控制器
 *
 * REST 路径前缀：/api/agent
 *
 * 主要职责：
 * 1. 用户私有 Agent 的 CRUD（按 createBy 隔离）
 * 2. 运行 Agent：把 Agent 引用的工具中心工具与 MCP server 解析为完整规格，
 *    再 POST 给 FastAPI /agent/run 执行真实的工具调用循环
 */
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

    /**
     * 查询当前用户的 Agent 列表
     *
     * GET /api/agent/list
     *
     * @return 当前用户拥有的 Agent 列表
     */
    @Operation(summary = "我的Agent列表")
    @GetMapping("/list")
    public Result<List<Agent>> list() {
        return Result.ok(agentService.listByUser(currentUserId()));
    }

    /**
     * 获取 Agent 详情
     *
     * GET /api/agent/{id}
     *
     * @param id Agent ID
     * @return Agent 详情（已校验归属）
     */
    @Operation(summary = "获取Agent详情")
    @GetMapping("/{id}")
    public Result<Agent> get(@PathVariable Long id) {
        return Result.ok(agentService.getOwned(id, currentUserId()));
    }

    /**
     * 新增 Agent
     *
     * POST /api/agent/add
     *
     * @param agent 待创建的 Agent
     * @return 操作结果
     */
    @Operation(summary = "新增Agent")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Agent agent) {
        agentService.create(agent, currentUserId());
        return Result.ok();
    }

    /**
     * 更新 Agent
     *
     * PUT /api/agent/update
     *
     * @param agent 待更新的 Agent
     * @return 操作结果
     */
    @Operation(summary = "更新Agent")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Agent agent) {
        agentService.update(agent, currentUserId());
        return Result.ok();
    }

    /**
     * 删除 Agent
     *
     * DELETE /api/agent/delete/{id}
     *
     * @param id Agent ID
     * @return 操作结果
     */
    @Operation(summary = "删除Agent")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id, currentUserId());
        return Result.ok();
    }

    /**
     * 运行 Agent
     *
     * POST /api/agent/{id}/run
     *
     * 运行时把 Agent 引用的 tools / mcp_servers 解析为完整规格（HTTP endpoint/config、SSE url），
     * 再 POST 给 FastAPI /agent/run，由其执行真实的工具调用循环并返回结果与执行轨迹（steps）。
     *
     * @param id      Agent ID
     * @param request 运行请求（用户输入、会话ID）
     * @return 包含 output 与 steps 的运行结果
     * @throws BusinessException Agent 已禁用或输入为空时抛出
     */
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
        // 将 Agent 引用的工具名/MCP 服务器名解析为 FastAPI 可直接执行的完整规格
        body.put("tools", resolveTools(agent.getTools(), userId));
        body.put("mcp_servers", resolveMcpServers(agent.getMcpServers(), userId));
        if (StringUtils.hasText(agent.getSystemPrompt())) {
            body.put("system_prompt", agent.getSystemPrompt());
        }
        if (StringUtils.hasText(agent.getModel())) {
            body.put("model", agent.getModel());
        }

        // 经统一的 FastApiClient 发起一元 JSON 调用，由 FastAPI 跑工具调用循环
        JsonNode data = fastApiClient.postForData("/agent/run", body);
        Map<String, Object> result = new HashMap<>();
        result.put("output", data.path("output").asText(""));
        result.put("steps", objectMapper.convertValue(data.path("steps"), List.class));
        return Result.ok(result);
    }

    /**
     * 将 Agent 引用的工具名解析为「本人拥有且已启用」的 HTTP tool 完整规格。
     *
     * 安全边界：仅遍历当前用户的工具，名称未匹配或未启用的一律跳过，避免引用他人工具。
     *
     * @param toolNamesJson 工具名 JSON 数组字符串
     * @param userId        当前用户ID
     * @return FastAPI 可执行的工具规格列表（name/description/endpoint/config）
     */
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
            // config 为 JSON 字符串（参数 schema / method / headers），解析为对象透传给 FastAPI
            spec.put("config", parseObject(tool.getConfig()));
            specs.add(spec);
        }
        return specs;
    }

    /**
     * 将 Agent 引用的 MCP server 名解析为「本人拥有且已启用」的 SSE server 规格。
     *
     * 仅支持 sse 传输：stdio 服务器无法在运行时经 HTTP 加载，故跳过。
     *
     * @param serverNamesJson MCP server 名 JSON 数组字符串
     * @param userId          当前用户ID
     * @return SSE server 规格列表（name/url/transport/headers）
     */
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

    /**
     * 从 Spring Security 上下文取出当前登录用户ID，用于资源归属隔离
     *
     * @return 当前登录用户ID
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
