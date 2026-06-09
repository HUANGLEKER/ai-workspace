/**
 * 系统监控 API（仅管理员）
 *
 * 对应后端 workspace-monitor 模块的 MonitorController，无 DB 表。
 * ServerInfo 数据来自 JDK MXBeans；ServiceHealth 通过探测 Redis/FastAPI/MinIO 获得。
 */
import request from './request'

/** 服务器运行时指标，由 JDK MXBeans 采集 */
export interface ServerInfo {
  cpu: { cores: number; sysUsedPercent: number; procUsedPercent: number }
  memory: { total: number; used: number; usedPercent: number }
  jvm: {
    version: string
    vendor: string
    uptime: number
    max: number
    total: number
    used: number
    usedPercent: number
  }
  os: { name: string; arch: string; version: string }
  disks: { path: string; total: number; used: number; usedPercent: number }[]
}

/** 单个依赖服务的健康检查结果 */
export interface ServiceHealth {
  name: string
  status: 'UP' | 'DOWN'
  /** 被探测的 URL 或地址 */
  target: string
  /** 探测延迟（毫秒），DOWN 时无意义 */
  latencyMs: number
  error: string | null
}

export const getServerInfo = () =>
  request.get<unknown, ServerInfo>('/monitor/server')

export const getServiceHealth = () =>
  request.get<unknown, ServiceHealth[]>('/monitor/health')
