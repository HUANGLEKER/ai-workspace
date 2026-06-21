import type { Component } from 'vue'
import {
  Activity,
  BookOpen,
  Bot,
  Boxes,
  Cpu,
  FileSearch,
  Folder,
  LayoutDashboard,
  MessageSquare,
  Plug,
  Sparkles,
  Timer,
  Users,
  Workflow,
  Wrench
} from 'lucide-vue-next'

export interface NavItem {
  path: string
  title: string
  icon: Component
}

export interface NavGroup {
  label: string
  admin?: boolean
  items: NavItem[]
}

export const navGroups: NavGroup[] = [
  {
    label: '',
    items: [
      { path: '/dashboard', title: '仪表盘', icon: LayoutDashboard },
      { path: '/chat', title: 'AI 对话', icon: MessageSquare }
    ]
  },
  {
    label: '知识',
    items: [
      { path: '/knowledge/base', title: '知识库', icon: BookOpen },
      { path: '/knowledge/rag', title: '知识库问答', icon: FileSearch }
    ]
  },
  {
    label: '创作与自动化',
    items: [
      { path: '/prompt', title: '提示词中心', icon: Sparkles },
      { path: '/agent', title: 'Agent', icon: Cpu },
      { path: '/workflow', title: '工作流', icon: Workflow }
    ]
  },
  {
    label: '资源',
    items: [
      { path: '/file', title: '文件中心', icon: Folder },
      { path: '/tool', title: '工具中心', icon: Wrench },
      { path: '/mcp', title: 'MCP 服务', icon: Plug }
    ]
  },
  {
    label: '系统',
    admin: true,
    items: [
      { path: '/monitor', title: '系统监控', icon: Activity },
      { path: '/system/user', title: '用户管理', icon: Users },
      { path: '/system/model', title: '模型管理', icon: Boxes },
      { path: '/system/job', title: '定时任务', icon: Timer }
    ]
  }
]

export const getVisibleNavGroups = (isAdmin: boolean) =>
  navGroups.filter((group) => !group.admin || isAdmin)

export const isNavItemActive = (currentPath: string, itemPath: string) =>
  currentPath === itemPath || currentPath.startsWith(itemPath + '/')

export const productIcon = Bot
