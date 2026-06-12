import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { MotionPlugin } from '@vueuse/motion'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 注册 VueUse Motion：提供 v-motion 指令，为消息入场等场景做声明式动效
app.use(MotionPlugin)

// 挂载前初始化主题：读取持久化偏好并应用 .dark，与 index.html 防闪烁脚本保持一致
useThemeStore()

app.mount('#app')
