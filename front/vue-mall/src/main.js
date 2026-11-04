import './assets/styles/index.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { configureAuthBridge } from './api/client'
import { refreshSession, setUnauthorizedHandler } from './api/auth'
import { getAccessToken } from './api/tokenStorage'
import { useUserStore } from './stores/user'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

function notifyUnauthorized(message) {
  useUserStore(pinia).handleUnauthorized(message)
}

// 必须在 app.use(router) 之前注册：初始路由守卫会立即恢复登录态并发起 /auth/me。
configureAuthBridge({
  getAccessToken,
  refresh: refreshSession,
  onUnauthorized: notifyUnauthorized,
})

setUnauthorizedHandler(notifyUnauthorized)

app.use(router)
app.mount('#app')