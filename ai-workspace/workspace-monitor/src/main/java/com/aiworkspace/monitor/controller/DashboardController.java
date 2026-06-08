package com.aiworkspace.monitor.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.monitor.dto.DashboardStatsVO;
import com.aiworkspace.monitor.service.DashboardService;
import com.aiworkspace.system.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘 Controller
 *
 * 提供首页概览统计，对应 REST 路径前缀 {@code /api/dashboard}。
 *
 * 安全说明：统计口径按当前登录用户隔离（而非全局），仅返回属于本人的资源计数。
 *
 * @since 2026
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 获取仪表盘统计数据
     *
     * <p>HTTP: {@code GET /api/dashboard/stats}
     *
     * <p>功能：返回当前用户今日会话数、知识库数、文档数、文件数等概览指标。
     *
     * @return 按当前用户隔离的统计结果
     */
    @Operation(summary = "获取统计数据")
    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats() {
        // 安全：从安全上下文取当前用户 id，确保统计仅覆盖本人资源，防止越权查看
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = loginUser.getSysUser().getId();
        return Result.ok(dashboardService.getStats(userId));
    }
}
