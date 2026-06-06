import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus components/directives, their per-component styles, and icons are
// all auto-imported on demand (see vite.config.ts) — no global EP import needed.
import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
