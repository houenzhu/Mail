/**
 * 路由表。全部业务页面挂在 AdminLayout 下（懒加载）。
 */
import AdminLayout from '@/components/layout/AdminLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/workbench',
    children: [
      {
        path: 'workbench',
        name: 'workbench',
        component: () => import('@/views/dashboard/WorkbenchView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'product/list',
        name: 'product-list',
        component: () => import('@/views/product/ProductListView.vue'),
        meta: { title: '我的商品' },
      },
      {
        path: 'product/create',
        name: 'product-create',
        component: () => import('@/views/product/ProductFormView.vue'),
        meta: { title: '发布商品' },
      },
      {
        path: 'product/edit/:id',
        name: 'product-edit',
        component: () => import('@/views/product/ProductFormView.vue'),
        meta: { title: '编辑商品' },
      },
      {
        path: 'category/list',
        name: 'category-list',
        component: () => import('@/views/category/CategoryListView.vue'),
        meta: { title: '分类与品牌' },
      },
      {
        path: 'inventory/list',
        name: 'inventory-list',
        component: () => import('@/views/inventory/InventoryListView.vue'),
        meta: { title: '库存管理' },
      },
      {
        path: 'inventory/record',
        name: 'inventory-record',
        component: () => import('@/views/inventory/InventoryRecordView.vue'),
        meta: { title: '库存流水' },
      },
      {
        path: 'order/list',
        name: 'order-list',
        component: () => import('@/views/order/OrderListView.vue'),
        meta: { title: '订单管理' },
      },
      {
        path: 'refund/list',
        name: 'refund-list',
        component: () => import('@/views/refund/RefundListView.vue'),
        meta: { title: '退款处理' },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '数据看板' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', public: true },
  },
]

export default routes
