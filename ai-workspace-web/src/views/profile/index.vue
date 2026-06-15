<template>
  <div class="mx-auto max-w-2xl pb-6">
    <div class="mb-6">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">个人信息</h2>
      <p class="mt-0.5 text-sm text-zinc-500 dark:text-zinc-400">查看并编辑你的资料与账号安全</p>
    </div>

    <!-- 资料卡 -->
    <AppCard title="基本资料">
      <!-- 头像 + 用户名/角色 -->
      <div class="mb-6 flex items-center gap-4">
        <div class="h-16 w-16 shrink-0 overflow-hidden rounded-2xl">
          <img v-if="avatarUrl" :src="avatarUrl" alt="头像" class="h-16 w-16 object-cover" />
          <AppAvatar v-else :icon="User" variant="dark" size="lg" class="!h-16 !w-16 !rounded-2xl" />
        </div>
        <div class="min-w-0">
          <div class="truncate text-base font-medium text-zinc-800 dark:text-zinc-100">
            {{ userInfo?.nickname || userInfo?.username }}
          </div>
          <div class="mt-1 flex flex-wrap gap-1.5">
            <AppTag v-for="r in userInfo?.roles || []" :key="r">{{ roleLabel(r) }}</AppTag>
          </div>
        </div>
        <div class="ml-auto">
          <AppUpload
            action="/api/auth/avatar"
            accept="image/*"
            :before-upload="beforeAvatarUpload"
            @success="onAvatarUploaded"
            @error="() => toast.error('头像上传失败')"
          >
            <AppButton :icon="Camera">更换头像</AppButton>
          </AppUpload>
        </div>
      </div>

      <AppFormItem label="用户名">
        <AppInput :model-value="userInfo?.username || ''" disabled />
      </AppFormItem>
      <AppFormItem label="昵称" required>
        <AppInput v-model="form.nickname" placeholder="请输入昵称" />
      </AppFormItem>
      <AppFormItem label="邮箱">
        <AppInput v-model="form.email" placeholder="请输入邮箱" />
      </AppFormItem>

      <div class="flex justify-end">
        <AppButton variant="primary" :loading="saving" @click="handleSaveProfile">保存修改</AppButton>
      </div>
    </AppCard>

    <!-- 安全卡 -->
    <div class="mt-4">
      <AppCard title="账号安全">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-sm text-zinc-800 dark:text-zinc-100">登录密码</div>
            <div class="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">建议定期修改以保证账号安全</div>
          </div>
          <AppButton :icon="Lock" @click="pwdDialogVisible = true">修改密码</AppButton>
        </div>
      </AppCard>
    </div>

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
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心页：编辑昵称/邮箱、更换头像、修改密码。
 * 头像经 /api/auth/avatar 直传，后端返回 1h 预签名 URL 即时回显；
 * 资料/头像保存后强制刷新 auth store，让 Header 的昵称与头像同步。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Camera } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { updateProfile, updatePassword } from '@/api/auth'
import {
  AppCard, AppFormItem, AppInput, AppButton, AppUpload, AppAvatar, AppTag, AppDialog, toast
} from '@/components/ui'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)

const avatarUrl = ref(userInfo.value?.avatar || '')

const form = reactive({
  nickname: userInfo.value?.nickname || '',
  email: userInfo.value?.email || ''
})

// userInfo 异步加载（刷新后路由守卫拉取）时回填表单
watch(userInfo, (info) => {
  if (!info) return
  form.nickname = info.nickname || ''
  form.email = info.email || ''
  avatarUrl.value = info.avatar || ''
}, { immediate: true })

const roleLabel = (code: string) => code.replace(/^ROLE_/, '')

// --- 保存资料 ---
const saving = ref(false)
const handleSaveProfile = async () => {
  if (!form.nickname.trim()) {
    toast.warning('请输入昵称')
    return
  }
  saving.value = true
  try {
    await updateProfile({ nickname: form.nickname.trim(), email: form.email.trim() })
    await authStore.refreshUserInfo()
    toast.success('资料已更新')
  } catch {
    // 错误在 request 拦截器中提示
  } finally {
    saving.value = false
  }
}

// --- 头像上传 ---
const beforeAvatarUpload = (file: File) => {
  if (!file.type.startsWith('image/')) {
    toast.warning('请选择图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    toast.warning('头像不能超过 5MB')
    return false
  }
  return true
}

const onAvatarUploaded = async (_file: File, body: unknown) => {
  const url = (body as { data?: { url?: string } })?.data?.url
  if (url) avatarUrl.value = url
  await authStore.refreshUserInfo()
  toast.success('头像已更新')
}

// --- 修改密码 ---
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

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
    pwdDialogVisible.value = false
    authStore.logout()
    router.push('/login')
  } catch {
    // 错误在 request 拦截器中提示
  } finally {
    pwdSubmitting.value = false
  }
}
</script>
