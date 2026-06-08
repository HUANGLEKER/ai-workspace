package com.aiworkspace.monitor.dto;

import lombok.Data;

/**
 * 依赖服务健康检查视图对象
 *
 * 承载单个依赖（Redis、FastAPI AI 服务、MinIO）的连通性与延迟探测结果。
 * 探针实现：Redis 用 PING 命令；FastAPI 探 {@code /health}；MinIO 探 {@code /minio/health/live}。
 *
 * @since 2026
 */
@Data
public class ServiceHealthVO {

    /** 依赖名称（如 Redis、FastAPI、MinIO） */
    private String name;
    /** 连通状态：UP 或 DOWN */
    private String status;
    /** 实际探测地址 */
    private String target;
    /** 往返延迟（毫秒）；DOWN 时为 -1 */
    private long latencyMs;
    /** DOWN 时的错误详情；UP 时为 null */
    private String error;

    public static ServiceHealthVO up(String name, String target, long latencyMs) {
        ServiceHealthVO vo = new ServiceHealthVO();
        vo.name = name;
        vo.target = target;
        vo.status = "UP";
        vo.latencyMs = latencyMs;
        return vo;
    }

    public static ServiceHealthVO down(String name, String target, String error) {
        ServiceHealthVO vo = new ServiceHealthVO();
        vo.name = name;
        vo.target = target;
        vo.status = "DOWN";
        vo.latencyMs = -1;
        vo.error = error;
        return vo;
    }
}
