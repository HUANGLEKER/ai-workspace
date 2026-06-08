package com.aiworkspace.job.controller;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.common.response.PageResult;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.job.entity.SysJob;
import com.aiworkspace.job.entity.SysJobLog;
import com.aiworkspace.job.handler.JobHandlerRegistry;
import com.aiworkspace.job.mapper.SysJobLogMapper;
import com.aiworkspace.job.service.SysJobService;
import com.aiworkspace.system.security.LoginUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务管理 Controller
 *
 * REST 路径前缀：/api/job
 *
 * 提供任务 CRUD、启停、手动触发及执行日志查询/清理。
 * 整个 Controller 经 @PreAuthorize("hasRole('ADMIN')") 锁定，仅管理员可访问。
 */
@Tag(name = "定时任务")
@RestController
@RequestMapping("/api/job")
@PreAuthorize("hasRole('ADMIN')")
public class SysJobController {

    private final SysJobService sysJobService;
    private final SysJobLogMapper sysJobLogMapper;
    private final JobHandlerRegistry handlerRegistry;

    public SysJobController(SysJobService sysJobService,
                            SysJobLogMapper sysJobLogMapper,
                            JobHandlerRegistry handlerRegistry) {
        this.sysJobService = sysJobService;
        this.sysJobLogMapper = sysJobLogMapper;
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * 分页查询任务列表
     *
     * GET /api/job/page
     *
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10
     * @param jobName 任务名称模糊过滤，可选
     * @return 任务分页结果
     */
    @Operation(summary = "任务分页列表")
    @GetMapping("/page")
    public Result<PageResult<SysJob>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String jobName) {
        return Result.ok(PageResult.of(sysJobService.pageJobs(page, size, jobName)));
    }

    /**
     * 列出所有可用任务处理器名称（供新增任务时下拉选择）
     *
     * GET /api/job/handlers
     *
     * @return 已注册的 JobHandler 名称列表
     */
    @Operation(summary = "可用任务处理器列表")
    @GetMapping("/handlers")
    public Result<List<String>> handlers() {
        return Result.ok(handlerRegistry.names());
    }

    /**
     * 新增任务
     *
     * POST /api/job/add
     *
     * @param job 任务定义（cron 与 invokeTarget 会在服务层校验）
     * @return 操作结果
     */
    @Operation(summary = "新增任务")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysJob job) {
        sysJobService.addJob(job, currentUserId());
        return Result.ok();
    }

    /**
     * 更新任务
     *
     * PUT /api/job/update
     *
     * @param job 任务定义（须含 ID）
     * @return 操作结果
     */
    @Operation(summary = "更新任务")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody SysJob job) {
        sysJobService.updateJob(job);
        return Result.ok();
    }

    /**
     * 删除任务
     *
     * DELETE /api/job/delete/{id}
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @Operation(summary = "删除任务")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysJobService.deleteJob(id);
        return Result.ok();
    }

    /**
     * 启用/暂停任务
     *
     * PUT /api/job/status
     *
     * @param body 含 id 与 status（0=运行，1=暂停）
     * @return 操作结果
     * @throws BusinessException id 或 status 缺失时抛出
     */
    @Operation(summary = "启用/暂停任务")
    @PutMapping("/status")
    public Result<Void> status(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object statusObj = body.get("status");
        if (idObj == null || statusObj == null) {
            throw new BusinessException("id 和 status 不能为空");
        }
        sysJobService.changeStatus(Long.valueOf(idObj.toString()), Integer.valueOf(statusObj.toString()));
        return Result.ok();
    }

    /**
     * 立即执行一次任务（不影响其 cron 调度）
     *
     * POST /api/job/run/{id}
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @Operation(summary = "立即执行一次")
    @PostMapping("/run/{id}")
    public Result<Void> run(@PathVariable Long id) {
        sysJobService.runOnce(id);
        return Result.ok();
    }

    /**
     * 分页查询任务执行日志
     *
     * GET /api/job/log/page
     *
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10
     * @param jobName 任务名称模糊过滤，可选
     * @param status  执行结果过滤（0=成功，1=失败），可选
     * @return 执行日志分页结果
     */
    @Operation(summary = "任务执行日志分页")
    @GetMapping("/log/page")
    public Result<PageResult<SysJobLog>> logPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) Integer status) {
        Page<SysJobLog> p = sysJobLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysJobLog>()
                        .like(StringUtils.hasText(jobName), SysJobLog::getJobName, jobName)
                        .eq(status != null, SysJobLog::getStatus, status)
                        .orderByDesc(SysJobLog::getId));
        return Result.ok(PageResult.of(p));
    }

    /**
     * 清空全部任务执行日志
     *
     * DELETE /api/job/log/clean
     *
     * 注意：sys_job_log 无软删除列，此处为物理删除（不可恢复）。
     *
     * @return 操作结果
     */
    @Operation(summary = "清空任务日志")
    @DeleteMapping("/log/clean")
    public Result<Void> cleanLog() {
        // 空条件 wrapper 表示删除全表记录（物理删除）
        sysJobLogMapper.delete(new LambdaQueryWrapper<>());
        return Result.ok();
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 从 Spring Security 上下文中取出的 LoginUser 用户 ID
     */
    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
