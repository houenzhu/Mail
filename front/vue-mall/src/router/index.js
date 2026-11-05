import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    if (to.name === from.name && to.path === from.path) return undefined
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '好物集 · 认真生活，精选好物' },
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchView.vue'),
      meta: { title: '搜索结果' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true, guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { title: '注册', public: true, guestOnly: true },
    },
    {
      path: '/product/:id',
      name: 'product-detail',
      component: () => import('@/views/ProductDetailView.vue'),
      meta: { title: '商品详情', public: true },
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('@/views/CartView.vue'),
      meta: { title: '购物车', requiresAuth: true },
    },
    {
      path: '/account',
      name: 'account',
      component: () => import('@/views/AccountView.vue'),
      meta: { title: '账户中心', requiresAuth: true },
    },
    {
      path: '/feature/:feature',
      name: 'feature',
      component: () => import('@/views/FeatureView.vue'),
      meta: { title: '功能预告', public: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面未找到', public: true },
    },
  ],
})

/**
 * 登录态守卫：
 * - 受保护页面未登录 → 跳登录并带 redirect；
 * - 已登录访问登录 / 注册 → 回到首页或 redirect；
 * - 刷新页面后只有令牌没有资料时，先拉 /auth/me 再放行。
 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (userStore.accessToken && !userStore.profile && !userStore.restoring) {
    await userStore.restore()
  }

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return {
      name: 'login',
      query: { redirect: to.fullPath, reason: 'required' },
    }
  }

  if (to.meta.guestOnly && userStore.isLoggedIn) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/'
    return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
  }

  return true
})

router.afterEach((to) => {
  const base = '好物集'
  document.title = to.meta?.title ? `${to.meta.title} | ${base}` : base
})

export default router
