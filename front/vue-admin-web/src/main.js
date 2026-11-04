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
import { onTokenStorageChange, setErrorNotifier, setUnauthorizedHandler } from '@/api/request'
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

/*
 * 跨标签页登录态同步。
 *
 * 一个标签页刷新令牌、登录或退出后，其它标签通过 `storage` 事件跟随：
 *  - 令牌被清除（注销/刷新失败）→ 本标签也清态并回登录页，避免继续用死令牌发请求；
 *  - 令牌被更新（其他标签完成刷新）→ 只同步内存令牌；
 *  - 从无令牌变为有令牌（其他标签完成登录）→ 重新拉取用户信息。
 *
 * 这里刻意不整页 reload：运营常开着多个查询页，强制刷新会丢失筛选条件。
 */
onTokenStorageChange(({ accessToken, refreshToken }) => {
  const userStore = useUserStore()
  if (!accessToken) {
    userStore.reset()
    if (router.currentRoute.value.name !== 'login') {
      router.replace({
        name: 'login',
        query: { redirect: router.currentRoute.value.fullPath },
      })
      ElMessage.warning('账号已在其他标签页退出登录')
    }
    return
  }

  const hadProfile = Boolean(userStore.profile)
  userStore.syncTokens({ accessToken, refreshToken })
  if (!hadProfile) {
    userStore.restore()
  }
})

app.mount('#app')
