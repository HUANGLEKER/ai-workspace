package com.aiworkspace.monitor.dto;

import lombok.Data;

/**
 * Connectivity/health of a dependency the platform relies on
 * (Redis, FastAPI AI service, MinIO).
 */
@Data
public class ServiceHealthVO {

    private String name;
    /** UP / DOWN */
    private String status;
    /** target address probed */
    private String target;
    /** round-trip latency in milliseconds, -1 if down */
    private long latencyMs;
    /** error detail when DOWN, null otherwise */
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
