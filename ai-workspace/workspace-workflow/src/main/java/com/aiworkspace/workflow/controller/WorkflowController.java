package com.aiworkspace.workflow.controller;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.framework.client.FastApiClient;
import com.aiworkspace.system.security.LoginUser;
import com.aiworkspace.workflow.dto.WorkflowRunRequest;
import com.aiworkspace.workflow.entity.Workflow;
import com.aiworkspace.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流管理控制器
 *
 * REST 路径前缀：/api/workflow
 *
 * 主要职责：
 * 1. 用户私有工作流的 CRUD（按 createBy 隔离）
 * 2. 运行工作流：代理到 FastAPI /workflow/run，由其 LangGraph 引擎执行
 */
@Tag(name = "工作流")
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;
    private final FastApiClient fastApiClient;

    public WorkflowController(WorkflowService workflowService, ObjectMapper objectMapper,
                             FastApiClient fastApiClient) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
        this.fastApiClient = fastApiClient;
    }

    /**
     * 查询当前用户的工作流列表
     *
     * GET /api/workflow/list
     *
     * @return 当前用户拥有的工作流列表
     */
    @Operation(summary = "我的工作流列表")
    @GetMapping("/list")
    public Result<List<Workflow>> list() {
        return Result.ok(workflowService.listByUser(currentUserId()));
    }

    /**
     * 获取工作流详情
     *
     * GET /api/workflow/{id}
     *
     * @param id 工作流ID
     * @return 工作流详情（已校验归属）
     */
    @Operation(summary = "获取工作流详情")
    @GetMapping("/{id}")
    public Result<Workflow> get(@PathVariable Long id) {
        return Result.ok(workflowService.getOwned(id, currentUserId()));
    }

    /**
     * 新增工作流
     *
     * POST /api/workflow/add
     *
     * @param workflow 待创建的工作流
     * @return 操作结果
     */
    @Operation(summary = "新增工作流")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Workflow workflow) {
        workflowService.create(workflow, currentUserId());
        return Result.ok();
    }

    /**
     * 更新工作流
     *
     * PUT /api/workflow/update
     *
     * @param workflow 待更新的工作流
     * @return 操作结果
     */
    @Operation(summary = "更新工作流")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Workflow workflow) {
        workflowService.update(workflow, currentUserId());
        return Result.ok();
    }

    /**
     * 删除工作流
     *
     * DELETE /api/workflow/delete/{id}
     *
     * @param id 工作流ID
     * @return 操作结果
     */
    @Operation(summary = "删除工作流")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id, currentUserId());
        return Result.ok();
    }

    /**
     * 运行工作流
     *
     * POST /api/workflow/{id}/run
     *
     * 校验归属与启用状态后，将工作流ID、输入参数、模型名组装为请求体，
     * 代理到 FastAPI /workflow/run 由 LangGraph 引擎执行，返回运行状态与输出。
     *
     * @param id      工作流ID
     * @param request 运行请求（输入参数、会话ID）
     * @return 包含 status 与 outputs 的运行结果
     * @throws BusinessException 工作流已禁用时抛出
     */
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

        // 经统一的 FastApiClient 代理到 FastAPI，由 LangGraph 引擎执行工作流
        JsonNode data = fastApiClient.postForData("/workflow/run", body);
        Map<String, Object> result = new HashMap<>();
        result.put("status", data.path("status").asText("failed"));
        result.put("outputs", objectMapper.convertValue(data.path("outputs"), Map.class));
        return Result.ok(result);
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
