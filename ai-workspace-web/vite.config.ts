import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import * as ElementPlusIcons from '@element-plus/icons-vue'

// 收集所有 Element Plus 图标名称，用于模板中直接使用 <Search/> 等图标组件时按需解析
// 不再全局注册图标集，避免首屏加载大量未使用的图标
const epIconNames = new Set(Object.keys(ElementPlusIcons))

export default defineConfig({
  plugins: [
    vue(),
    // 按需自动引入 ElMessage、ElMessageBox 等 Element Plus 程序化 API 及其样式，
    // 无需手动 import，也无需在 main.ts 全局注册
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts'  // 生成类型声明文件，需提交到版本库（vue-tsc 先于 Vite 运行）
    }),
    // 按需注册 <el-*> 组件/指令及其样式；另通过自定义解析器支持模板中直接使用图标组件
    Components({
      resolvers: [
        ElementPlusResolver(),
        {
          type: 'component',
          // 模板中的裸图标组件名（如 <Search/>）映射到 @element-plus/icons-vue
          resolve: (name: string) =>
            epIconNames.has(name) ? { name, from: '@element-plus/icons-vue' } : undefined
        }
      ],
      dts: 'src/components.d.ts'  // 生成类型声明文件，需提交到版本库
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
        // Element Plus 刻意不强制拆分，由 Rollup 按路由自动分包，
        // 使登录页/首屏只加载当前页面实际用到的组件，避免首包臃肿
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
      // 将 /api 前缀的请求代理到 Spring Boot，避免浏览器跨域限制
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
