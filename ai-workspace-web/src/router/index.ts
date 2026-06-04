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
          meta: { title: '仪表盘', icon: 'Odometer' }
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/chat/index.vue'),
          meta: { title: 'AI 对话', icon: 'ChatDotRound' }
        },
        {
          path: 'knowledge/base',
          name: 'KnowledgeBase',
          component: () => import('@/views/knowledge/base/index.vue'),
          meta: { title: '知识库管理', icon: 'Reading' }
        },
        {
          path: 'knowledge/document',
          name: 'KnowledgeDocument',
          component: () => import('@/views/knowledge/document/index.vue'),
          meta: { title: '文档管理', icon: 'Document' }
        },
        {
          path: 'file',
          name: 'File',
          component: () => import('@/views/file/index.vue'),
          meta: { title: '文件中心', icon: 'Folder' }
        },
        {
          path: 'prompt',
          name: 'Prompt',
          component: () => import('@/views/prompt/index.vue'),
          meta: { title: '提示词中心', icon: 'MagicStick' }
        },
        {
          path: 'workflow',
          name: 'Workflow',
          component: () => import('@/views/workflow/index.vue'),
          meta: { title: '工作流', icon: 'Connection' }
        },
        {
          path: 'agent',
          name: 'Agent',
          component: () => import('@/views/agent/index.vue'),
          meta: { title: 'Agent', icon: 'Cpu' }
        },
        {
          path: 'monitor',
          name: 'Monitor',
          component: () => import('@/views/monitor/index.vue'),
          meta: { title: '监控', icon: 'Monitor' }
        },
        {
          path: 'system/user',
          name: 'SystemUser',
          component: () => import('@/views/system/user/index.vue'),
          meta: { title: '用户管理', icon: 'User' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth === false) {
    if (to.path === '/login' && authStore.isLoggedIn) {
      next('/')
    } else {
      next()
    }
  } else {
    if (!authStore.isLoggedIn) {
      next({ path: '/login', query: { redirect: to.fullPath } })
    } else {
      next()
    }
  }
})

export default router
