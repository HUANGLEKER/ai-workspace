import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    // 自动按需引入 Vue / Vue Router / Pinia 的常用 API（ref、computed、useRouter 等），
    // 无需在每个 <script setup> 顶部手写 import。类型声明输出到 src/types 以便 tsconfig 纳入。
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/types/auto-imports.d.ts',
      eslintrc: { enabled: false }
    }),
    // 自动注册 src/components/ui 下的统一组件库，模板中可直接使用 <AppButton /> 等而无需 import。
    // 注意：toast/confirm/alertBox 为函数，仍需从 '@/components/ui' 显式引入。
    Components({
      dirs: ['src/components'],
      extensions: ['vue'],
      dts: 'src/types/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    rollupOptions: {
      output: {
        // 仅强制拆分两个必要的 chunk：
        //   vue-vendor  — 框架核心（vue/vue-router/pinia），每个路由都需要，单独缓存
        //   markdown    — markdown-it + highlight.js 体积较大，仅 Chat/RAG 页面使用，随路由懒加载
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('markdown-it') || id.includes('highlight.js')) return 'markdown'
          if (/node_modules[\\/](vue|vue-router|pinia|@vue)[\\/]/.test(id)) return 'vue-vendor'
        }
      }
    }
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      // 将 /api 前缀的请求代理到 Go 后端，避免浏览器跨域限制
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
