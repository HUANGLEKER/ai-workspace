import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import * as ElementPlusIcons from '@element-plus/icons-vue'

// Names of every Element Plus icon, used to auto-import icons referenced as
// bare components in templates (e.g. <Search/>) now that we no longer globally
// register the whole icon set.
const epIconNames = new Set(Object.keys(ElementPlusIcons))

export default defineConfig({
  plugins: [
    vue(),
    // Auto-import the programmatic Element Plus APIs (ElMessage, ElMessageBox, …)
    // together with their styles, so we can drop the explicit imports + global CSS.
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts'
    }),
    // On-demand registration of <el-*> components/directives and their styles,
    // plus Element Plus icons used directly in templates.
    Components({
      resolvers: [
        ElementPlusResolver(),
        {
          type: 'component',
          resolve: (name: string) =>
            epIconNames.has(name) ? { name, from: '@element-plus/icons-vue' } : undefined
        }
      ],
      dts: 'src/components.d.ts'
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
        // Only force-chunk the always-needed framework core and the heavy
        // markdown stack (used solely by chat/RAG, so it stays lazy). Element
        // Plus and its deps are intentionally left to Rollup's per-route
        // splitting: the login/first paint pulls just the few components it
        // uses, while heavy widgets (tables, date pickers, …) load with their
        // own route chunks rather than all up front.
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
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
