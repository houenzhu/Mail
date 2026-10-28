import { createRouter, createWebHistory } from 'vue-router'

import routes from './routes'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

const APP_TITLE = '商场管理后台'

/**
 * 全局前置守卫：登录态 + 后台准入。
 * 用 return 风格的守卫（不调用 next()）。
 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 刷新页面后：有令牌但没有用户信息 → 拉一次 /auth/me 恢复
  // （内部失败会自清登录态，不会留下"有令牌没用户"的半登录状态）
  if (userStore.accessToken && !userStore.profile) {
    await userStore.restore()
  }

  if (to.meta.public) {
    // 已登录用户访问登录页 → 直接进工作台
    if (to.name === 'login' && userStore.isLoggedIn && userStore.isStaff) {
      return { path: '/workbench' }
    }
    return true
  }

  if (!userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!userStore.isStaff) {
    // 已登录但不是 staff：清态并给出原因，避免"登录成功却进不去"的困惑
    userStore.reset()
    return { name: 'login', query: { denied: '1' } }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - ${APP_TITLE}` : APP_TITLE
})

export default router
