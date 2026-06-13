<template>
  <!-- 强制改密引导（P3-6）：默认密码未改时弹出；visible 的 setter 为 no-op，关闭操作无效 -->
  <AppDialog v-model="visible" title="请先修改默认密码" width="420px">
    <p class="mb-3 text-sm text-zinc-500 dark:text-zinc-400">为账号安全，首次登录需修改默认密码后才能继续使用。</p>
    <AppFormItem label="新密码" required>
      <AppInput v-model="form.newPassword" type="password" placeholder="至少 6 位" />
    </AppFormItem>
    <AppFormItem label="确认新密码" required>
      <AppInput v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" />
    </AppFormItem>
    <template #footer>
      <AppButton variant="primary" :loading="submitting" @click="submit">确认修改</AppButton>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { updatePassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { AppDialog, AppFormItem, AppInput, AppButton, toast } from '@/components/ui'

const authStore = useAuthStore()
const { userInfo } = storeToRefs(authStore)

// 默认密码（init.sql 种子 admin/123456）作为原密码提交
const DEFAULT_PWD = '123456'
// setter 为 no-op：用户点 X/遮罩想关闭时被忽略，只有改密成功（清标记）才真正消失
const visible = computed({
  get: () => !!userInfo.value?.mustChangePwd,
  set: () => {}
})
const submitting = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '' })

async function submit() {
  if (form.newPassword.length < 6) {
    toast.warning('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    toast.warning('两次密码不一致')
    return
  }
  if (form.newPassword === DEFAULT_PWD) {
    toast.warning('新密码不能与默认密码相同')
    return
  }
  submitting.value = true
  try {
    await updatePassword({ oldPassword: DEFAULT_PWD, newPassword: form.newPassword })
    // 清除强制改密标记：本地立即生效，避免再次弹出
    if (userInfo.value) userInfo.value.mustChangePwd = false
    toast.success('密码已修改')
  } catch {
    toast.error('修改失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>
