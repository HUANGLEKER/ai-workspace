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

    @Operation(summary = "任务分页列表")
    @GetMapping("/page")
    public Result<PageResult<SysJob>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String jobName) {
        return Result.ok(PageResult.of(sysJobService.pageJobs(page, size, jobName)));
    }

    @Operation(summary = "可用任务处理器列表")
    @GetMapping("/handlers")
    public Result<List<String>> handlers() {
        return Result.ok(handlerRegistry.names());
    }

    @Operation(summary = "新增任务")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysJob job) {
        sysJobService.addJob(job, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "更新任务")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody SysJob job) {
        sysJobService.updateJob(job);
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysJobService.deleteJob(id);
        return Result.ok();
    }

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

    @Operation(summary = "立即执行一次")
    @PostMapping("/run/{id}")
    public Result<Void> run(@PathVariable Long id) {
        sysJobService.runOnce(id);
        return Result.ok();
    }

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

    @Operation(summary = "清空任务日志")
    @DeleteMapping("/log/clean")
    public Result<Void> cleanLog() {
        sysJobLogMapper.delete(new LambdaQueryWrapper<>());
        return Result.ok();
    }

    private Long currentUserId() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getSysUser().getId();
    }
}
