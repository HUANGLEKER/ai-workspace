import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
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
