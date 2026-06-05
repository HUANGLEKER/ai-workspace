package com.aiworkspace.monitor.controller;

import com.aiworkspace.common.response.Result;
import com.aiworkspace.monitor.dto.ServerInfoVO;
import com.aiworkspace.monitor.dto.ServiceHealthVO;
import com.aiworkspace.monitor.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "系统监控")
@RestController
@RequestMapping("/api/monitor")
@PreAuthorize("hasRole('ADMIN')")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Operation(summary = "服务器运行时指标")
    @GetMapping("/server")
    public Result<ServerInfoVO> server() {
        return Result.ok(monitorService.getServerInfo());
    }

    @Operation(summary = "依赖服务健康检查")
    @GetMapping("/health")
    public Result<List<ServiceHealthVO>> health() {
        return Result.ok(monitorService.getServiceHealth());
    }
}
