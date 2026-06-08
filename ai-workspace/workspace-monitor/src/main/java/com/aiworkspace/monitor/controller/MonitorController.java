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

/**
 * 系统监控 Controller
 *
 * 提供服务器运行时指标与依赖服务健康检查，对应 REST 路径前缀 {@code /api/monitor}。
 *
 * 安全说明：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 锁定，仅管理员可访问；
 * 运维监控数据敏感，普通用户无权查看。该模块不依赖任何 DB 表。
 *
 * @since 2026
 */
@Tag(name = "系统监控")
@RestController
@RequestMapping("/api/monitor")
@PreAuthorize("hasRole('ADMIN')")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 服务器运行时指标
     *
     * <p>HTTP: {@code GET /api/monitor/server}
     *
     * <p>功能：实时采集 CPU、内存、JVM、磁盘、OS 等运行时指标（取自 JDK MXBean）。
     *
     * @return 服务器运行时指标快照
     */
    @Operation(summary = "服务器运行时指标")
    @GetMapping("/server")
    public Result<ServerInfoVO> server() {
        return Result.ok(monitorService.getServerInfo());
    }

    /**
     * 依赖服务健康检查
     *
     * <p>HTTP: {@code GET /api/monitor/health}
     *
     * <p>功能：探测平台依赖（Redis、FastAPI、MinIO）的连通性与延迟。
     *
     * @return 各依赖服务的健康状态列表
     */
    @Operation(summary = "依赖服务健康检查")
    @GetMapping("/health")
    public Result<List<ServiceHealthVO>> health() {
        return Result.ok(monitorService.getServiceHealth());
    }
}
