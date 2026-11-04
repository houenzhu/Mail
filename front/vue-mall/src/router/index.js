import { createRouter, createWebHistory } from 'vue-router'

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
      path: '/feature/:feature',
      name: 'feature',
      component: () => import('@/views/FeatureView.vue'),
      meta: { title: '功能预告' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
})

router.afterEach((to) => {
  const base = '好物集'
  document.title = to.meta?.title ? `${to.meta.title} | ${base}` : base
})

export default router
