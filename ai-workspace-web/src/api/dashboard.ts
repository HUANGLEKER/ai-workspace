/**
 * 仪表盘统计 API
 *
 * 返回当前登录用户维度的统计数据（非全局），由后端按 userId 隔离查询。
 */
import request from './request'

/** 仪表盘统计卡片数据，全部按当前用户范围统计 */
export interface DashboardStats {
  /** 今日发起的对话会话数 */
  todaySessions: number
  /** 当前用户创建的知识库数量 */
  kbCount: number
  /** 当前用户上传的文档总数 */
  docCount: number
  /** 当前用户上传的文件总数 */
  fileCount: number
}

export const getDashboardStats = () =>
  request.get<unknown, DashboardStats>('/dashboard/stats')
