<template>
  <div class="flex min-h-screen items-center justify-center bg-zinc-50">
    <div class="w-[400px] max-w-[calc(100vw-2rem)] rounded-2xl border border-zinc-200/80 bg-white p-10 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)]">
      <div class="mb-8 flex flex-col items-center gap-3">
        <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-zinc-900">
          <Bot class="h-6 w-6 text-white" />
        </div>
        <h1 class="text-xl font-semibold text-zinc-800">AI Workspace</h1>
        <p class="text-sm text-zinc-500">登录你的个人 AI 中枢</p>
      </div>

      <form class="flex flex-col gap-4" @submit.prevent="handleLogin">
        <AppInput v-model="form.username" :icon="User" placeholder="请输入用户名" @enter="handleLogin" />
        <AppInput v-model="form.password" :icon="Lock" type="password" placeholder="请输入密码" @enter="handleLogin" />
        <AppButton variant="primary" size="lg" block native-type="submit" :loading="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </AppButton>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页：用户名/密码登录，获取 JWT token 并写入 Pinia Store。
 * 登录成功后跳转到 redirect 参数指定的页面（路由守卫在未登录时注入），默认仪表盘。
 */
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Bot, User, Lock } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { login } from '@/api/auth'
import { AppInput, AppButton, toast } from '@/components/ui'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const form = reactive({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.username.trim() || !form.password) {
    toast.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    authStore.setToken(res.token)
    toast.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>
