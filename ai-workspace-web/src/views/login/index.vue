<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <el-icon size="40" color="#409EFF">
          <Cpu/>
        </el-icon>
        <h1>AI Workspace</h1>
      </div>

      <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              size="large"
              :loading="loading"
              style="width: 100%"
              @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

/**
 * 登录页
 *
 * 功能：
 * 1. 用户名/密码登录，获取 JWT token 并写入 Pinia Store
 * 2. 登录成功后跳转到 redirect 参数指定的页面（路由守卫在未登录时注入），默认仪表盘
 */
<script setup lang="ts">
import {ref, reactive} from 'vue'
import {useRouter, useRoute} from 'vue-router'
import type {FormInstance, FormRules} from 'element-plus'
import {User, Lock} from '@element-plus/icons-vue'
import {useAuthStore} from '@/stores/auth'
import {login} from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{required: true, message: '请输入用户名', trigger: 'blur'}],
  password: [{required: true, message: '请输入密码', trigger: 'blur'}]
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form)
    authStore.setToken(res.token)
    ElMessage.success('登录成功')
    // 路由守卫未登录时会把原始目标路径附加为 redirect，登录后恢复导航
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d2535 0%, #2d3748 100%);
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 12px 0 8px;
}

.login-header p {
  color: #909399;
  font-size: 14px;
}

.login-tip {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}
</style>
