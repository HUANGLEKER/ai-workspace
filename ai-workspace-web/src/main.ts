import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件、指令、样式及图标均通过 unplugin 按需自动引入（见 vite.config.ts）
// 不需要也不应该在此处全局 app.use(ElementPlus)，否则会导致重复注册和样式冗余
import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
