// ⚠️ 顺序不能颠倒：Element Plus 的 dist/index.css 里也声明了 :root{--el-color-primary:#409eff}
// 等变量，同为 :root 选择器时**靠后的声明胜出**。因此必须先引组件库样式、
// 再引自己的主题变量，否则自定义主色会被覆盖回默认蓝。
import 'element-plus/dist/index.css'
import '@/assets/styles/index.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import App from './App.vue'
import router from './router'
import { setErrorNotifier, setUnauthorizedHandler } from '@/api/request'
import { useUserStore } from '@/stores/user'

const app = createApp(App)

// 图标全量注册（后台菜单/操作图标用量大，逐个按需注册收益有限）
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

// 统一错误提示：业务失败的错误文案由后端 message 决定（对齐需求文档 18.3）
setErrorNotifier((message) => {
  ElMessage.error(message)
})

// 令牌彻底失效（刷新也救不回来）→ 清态并跳登录页
setUnauthorizedHandler((message) => {
  const userStore = useUserStore()
  userStore.reset()
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
  if (message) ElMessage.warning(message)
})

app.mount('#app')
