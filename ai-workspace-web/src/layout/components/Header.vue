<template>
  <header
    class="sticky top-0 z-50 flex h-14 shrink-0 items-center justify-between border-b border-zinc-200/80 bg-white px-6 dark:border-zinc-800 dark:bg-zinc-900"
  >
    <div class="min-w-0">
      <div class="flex items-center gap-1.5 text-xs text-zinc-400 dark:text-zinc-500">
        <router-link to="/" class="transition-all duration-200 ease-out hover:text-zinc-700 dark:hover:text-zinc-200">
          工作台
        </router-link>
        <ChevronRight class="h-3.5 w-3.5" />
        <span class="truncate">{{ currentSection }}</span>
      </div>
      <div class="mt-0.5 truncate text-sm font-semibold text-zinc-900 dark:text-zinc-100">{{ currentTitle }}</div>
    </div>

    <div class="flex items-center gap-2">
      <AppTooltip :content="themeTooltip" side="bottom">
        <button
          class="rounded-xl p-2 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
          @click="themeStore.cycle()"
        >
          <component :is="themeIcon" class="h-4 w-4" />
        </button>
      </AppTooltip>

      <AppTooltip content="帮助文档" side="bottom">
        <button
          class="rounded-xl p-2 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
        >
          <CircleHelp class="h-4 w-4" />
        </button>
      </AppTooltip>

      <AppDropdown :items="menuItems" @select="handleCommand">
        <button
          class="flex items-center gap-2 rounded-xl px-2 py-1.5 transition-all duration-200 ease-out hover:bg-zinc-100/50 focus:outline-none dark:hover:bg-zinc-800"
        >
          <AppAvatar :icon="User" variant="dark" size="sm" />
          <span class="max-w-[112px] truncate text-sm text-zinc-800 dark:text-zinc-100">
            {{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}
          </span>
          <ChevronDown class="h-3.5 w-3.5 text-zinc-400" />
        </button>
      </AppDropdown>
    </div>
  </header>

  <AppDialog v-model="pwdDialogVisible" title="修改密码" width="400px" @close="resetPwdForm">
    <AppFormItem label="原密码" required>
      <AppInput v-model="pwdForm.oldPassword" type="password" placeholder="请输入原密码" />
    </AppFormItem>
    <AppFormItem label="新密码" required>
      <AppInput v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码（至少 6 位）" />
    </AppFormItem>
    <AppFormItem label="确认新密码" required>
      <AppInput v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
    </AppFormItem>
    <template #footer>
      <AppButton @click="pwdDialogVisible = false">取消</AppButton>
      <AppButton variant="primary" :loading="pwdSubmitting" @click="handleUpdatePassword">确认修改</AppButton>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, ChevronRight, CircleHelp, Lock, LogOut, MonitorCog, Moon, Sun, User } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { logout, updatePassword } from '@/api/auth'
import {
  AppAvatar,
  AppButton,
  AppDialog,
  AppDropdown,
  AppFormItem,
  AppInput,
  AppTooltip,
  confirm,
  toast,
  type DropdownItem
} from '@/components/ui'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const currentTitle = computed(() => (route.meta.title as string) || '工作台')
const currentSection = computed(() => {
  const first = route.path.split('/').filter(Boolean)[0]
  return (
    {
      dashboard: '概览',
      chat: '对话',
      knowledge: '知识',
      file: '资源',
      prompt: '创作与自动化',
      workflow: '创作与自动化',
      agent: '创作与自动化',
      tool: '资源',
      mcp: '资源',
      monitor: '系统',
      system: '系统',
      profile: '账户'
    }[first] || '工作台'
  )
})

const themeIcon = computed(() =>
  themeStore.preference === 'light' ? Sun : themeStore.preference === 'dark' ? Moon : MonitorCog
)
const themeTooltip = computed(() =>
  themeStore.preference === 'light'
    ? '亮色（点击切换暗色）'
    : themeStore.preference === 'dark'
      ? '暗色（点击切换跟随系统）'
      : '跟随系统（点击切换亮色）'
)

const menuItems: DropdownItem[] = [
  { key: 'profile', label: '个人信息', icon: User },
  { key: 'password', label: '修改密码', icon: Lock },
  { key: 'logout', label: '退出登录', icon: LogOut, danger: true, divided: true }
]

const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const resetPwdForm = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
}

const handleCommand = async (cmd: string) => {
  if (cmd === 'logout') {
    const ok = await confirm({
      title: '退出确认',
      message: '确定要退出登录吗？',
      confirmText: '确定退出',
      danger: true
    })
    if (!ok) return
    try {
      await logout()
    } catch {}
    authStore.logout()
    router.push('/login')
    return
  }

  if (cmd === 'profile') {
    router.push('/profile')
    return
  }

  if (cmd === 'password') {
    pwdDialogVisible.value = true
  }
}

const handleUpdatePassword = async () => {
  if (!pwdForm.oldPassword) {
    toast.warning('请输入原密码')
    return
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    toast.warning('新密码至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    toast.warning('两次输入的新密码不一致')
    return
  }

  pwdSubmitting.value = true
  try {
    await updatePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    pwdDialogVisible.value = false
    authStore.logout()
    router.push('/login')
  } finally {
    pwdSubmitting.value = false
  }
}
</script>
