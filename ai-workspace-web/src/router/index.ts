import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Layout from '@/layout/index.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/index.vue'),
      meta: { title: '登录', requiresAuth: false }
    },
    {
      path: '/',
      component: Layout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '仪表盘' }
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/profile/index.vue'),
          meta: { title: '个人信息' }
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/chat/index.vue'),
          meta: { title: 'AI 对话', fullPage: true }
        },
        {
          path: 'knowledge/base',
          name: 'KnowledgeBase',
          component: () => import('@/views/knowledge/base/index.vue'),
          meta: { title: '知识库' }
        },
        {
          path: 'knowledge/document',
          name: 'KnowledgeDocument',
          component: () => import('@/views/knowledge/document/index.vue'),
          meta: { title: '文档管理' }
        },
        {
          path: 'knowledge/rag',
          name: 'KnowledgeRag',
          component: () => import('@/views/knowledge/rag/index.vue'),
          meta: { title: '知识库问答', fullPage: true }
        },
        {
          path: 'file',
          name: 'File',
          component: () => import('@/views/file/index.vue'),
          meta: { title: '文件中心' }
        },
        {
          path: 'prompt',
          name: 'Prompt',
          component: () => import('@/views/prompt/index.vue'),
          meta: { title: '提示词中心' }
        },
        {
          path: 'workflow',
          name: 'Workflow',
          component: () => import('@/views/workflow/index.vue'),
          meta: { title: '工作流' }
        },
        {
          path: 'agent',
          name: 'Agent',
          component: () => import('@/views/agent/index.vue'),
          meta: { title: 'Agent' }
        },
        {
          path: 'tool',
          name: 'Tool',
          component: () => import('@/views/tool/index.vue'),
          meta: { title: '工具中心' }
        },
        {
          path: 'mcp',
          name: 'Mcp',
          component: () => import('@/views/mcp/index.vue'),
          meta: { title: 'MCP 服务' }
        },
        {
          path: 'monitor',
          name: 'Monitor',
          component: () => import('@/views/monitor/index.vue'),
          meta: { title: '系统监控', requiresAdmin: true }
        },
        {
          path: 'system/user',
          name: 'SystemUser',
          component: () => import('@/views/system/user/index.vue'),
          meta: { title: '用户管理', requiresAdmin: true }
        },
        {
          path: 'system/model',
          name: 'SystemModel',
          component: () => import('@/views/system/model/index.vue'),
          meta: { title: '模型管理', requiresAdmin: true }
        },
        {
          path: 'system/job',
          name: 'SystemJob',
          component: () => import('@/views/system/job/index.vue'),
          meta: { title: '定时任务', requiresAdmin: true }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth === false) {
    if (to.path === '/login' && authStore.isLoggedIn) next('/')
    else next()
    return
  }

  if (!authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (!authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch (err) {
      console.warn('[router] failed to fetch user info', err)
    }
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    next('/dashboard')
    return
  }

  next()
})

export default router
