import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'node:url'

// 独立于 vite.config：测试不需要 vue 插件/tailwind/自动导入，保持最小依赖面
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/__tests__/*.spec.ts']
  }
})
