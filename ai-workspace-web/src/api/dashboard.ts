import request from './request'

export interface DashboardStats {
  todaySessions: number
  kbCount: number
  docCount: number
  fileCount: number
}

export const getDashboardStats = () =>
  request.get<unknown, DashboardStats>('/dashboard/stats')
