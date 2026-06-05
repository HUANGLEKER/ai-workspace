import request from './request'

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

export interface ServiceHealth {
  name: string
  status: 'UP' | 'DOWN'
  target: string
  latencyMs: number
  error: string | null
}

export const getServerInfo = () =>
  request.get<unknown, ServerInfo>('/monitor/server')

export const getServiceHealth = () =>
  request.get<unknown, ServiceHealth[]>('/monitor/health')
