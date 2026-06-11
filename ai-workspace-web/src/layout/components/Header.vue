<template>
  <header class="sticky top-0 z-50 flex h-14 shrink-0 items-center justify-between border-b border-zinc-200/80 bg-white px-6 dark:border-zinc-800 dark:bg-zinc-900">
    <!-- 面包屑 -->
    <div class="flex items-center gap-1.5 text-sm">
      <router-link to="/" class="text-zinc-500 transition-all duration-200 ease-out hover:text-zinc-800 dark:hover:text-zinc-100">首页</router-link>
      <ChevronRight class="h-3.5 w-3.5 text-zinc-400" />
      <span class="text-zinc-800 dark:text-zinc-100">{{ currentTitle }}</span>
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
        <button class="rounded-xl p-2 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100">
          <CircleHelp class="h-4 w-4" />
        </button>
      </AppTooltip>

      <AppDropdown :items="menuItems" @select="handleCommand">
        <button
          class="flex items-center gap-2 rounded-xl px-2 py-1.5 transition-all duration-200 ease-out hover:bg-zinc-100/50 focus:outline-none dark:hover:bg-zinc-800"
        >
          <AppAvatar :icon="User" variant="dark" size="sm" />
          <span class="max-w-[100px] truncate text-sm text-zinc-800 dark:text-zinc-100">
            {{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}
          </span>
          <ChevronDown class="h-3.5 w-3.5 text-zinc-400" />
        </button>
      </AppDropdown>
    </div>
  </header>

  <!-- 修改密码弹窗 -->
  <AppDialog v-model="pwdDialogVisible" title="修改密码" width="400px" @close="resetPwdForm">
    <AppFormItem label="原密码" required>
      <AppInput v-model="pwdForm.oldPassword" type="password" placeholder="请输入原密码" />
    </AppFormItem>
    <AppFormItem label="新密码" required>
      <AppInput v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码（至少6位）" />
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
/**
 * 顶部 Header：面包屑 + 用户菜单（个人信息 / 修改密码 / 退出登录）
 * 退出登录先调接口使服务端 token 失效，再清除本地 Store。
 */
import { ChevronRight, ChevronDown, CircleHelp, User, Lock, LogOut, Sun, Moon, MonitorCog } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { logout, updatePassword } from '@/api/auth'
import { confirm, alertBox, toast, AppDialog, AppFormItem, AppInput, AppButton, type DropdownItem } from '@/components/ui'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const currentTitle = computed(() => (route.meta.title as string) || '')

// 主题按钮图标与提示随当前偏好切换（亮/暗/跟随系统）
const themeIcon = computed(() =>
  themeStore.preference === 'light' ? Sun : themeStore.preference === 'dark' ? Moon : MonitorCog
)
const themeTooltip = computed(() =>
  themeStore.preference === 'light' ? '亮色（点击切换暗色）'
    : themeStore.preference === 'dark' ? '暗色（点击切换跟随系统）'
      : '跟随系统（点击切换亮色）'
)

const menuItems: DropdownItem[] = [
  { key: 'profile', label: '个人信息', icon: User },
  { key: 'password', label: '修改密码', icon: Lock },
  { key: 'logout', label: '退出登录', icon: LogOut, danger: true, divided: true }
]

const handleCommand = async (cmd: string) => {
  if (cmd === 'logout') {
    const ok = await confirm({
      title: '退出确认',
      message: '确定要退出登录吗？',
      confirmText: '确定退出',
      danger: true
    })
    if (!ok) return
    try { await logout() } catch {}
    authStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    alertBox({ message: '个人信息功能即将上线' })
  } else if (cmd === 'password') {
    pwdDialogVisible.value = true
  }
}

// --- 修改密码逻辑 ---
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

const handleUpdatePassword = async () => {
  if (!pwdForm.oldPassword) {
    toast.warning('请输入原密码')
    return
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    toast.warning('新密码至少6位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    toast.warning('两次输入的新密码不一致')
    return
  }

  pwdSubmitting.value = true
  try {
    await updatePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    // 修改成功后后端返回了 toast message（通过 request 拦截器展示）
    pwdDialogVisible.value = false
    authStore.logout()
    router.push('/login')
  } catch (error) {
    // 错误在 request 拦截器中提示
  } finally {
    pwdSubmitting.value = false
  }
}
</script>
