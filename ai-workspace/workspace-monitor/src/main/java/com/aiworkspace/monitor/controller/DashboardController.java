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

@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "获取统计数据")
    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = loginUser.getSysUser().getId();
        return Result.ok(dashboardService.getStats(userId));
    }
}
