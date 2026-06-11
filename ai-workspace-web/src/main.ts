import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 挂载前初始化主题：读取持久化偏好并应用 .dark，与 index.html 防闪烁脚本保持一致
useThemeStore()

app.mount('#app')
