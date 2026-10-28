/**
 * 左侧菜单树（两级）——对齐千牛「业务域 → 二级功能」结构。
 *
 * roles：可见该菜单所需的角色（任一命中即可）；为空表示所有 staff 可见。
 * 角色取值对齐后端 UserRole（USER / OPERATOR / CS / ADMIN，见需求文档 16.1）。
 *
 * ⚠️ 前端菜单可见性只是体验优化，真正的权限边界在后端（见计划文档 6.4 / G-01）。
 */
export const MENU_TREE = [
  {
    key: 'workbench',
    title: '工作台',
    icon: 'HomeFilled',
    path: '/workbench',
  },
  {
    key: 'product',
    title: '商品',
    icon: 'Goods',
    children: [
      { key: 'product-list', title: '我的商品', path: '/product/list' },
      { key: 'product-create', title: '发布商品', path: '/product/create' },
      { key: 'category-list', title: '分类与品牌', path: '/category/list' },
      { key: 'inventory-list', title: '库存管理', path: '/inventory/list' },
    ],
  },
  {
    key: 'trade',
    title: '交易',
    icon: 'Tickets',
    children: [
      { key: 'order-list', title: '订单管理', path: '/order/list' },
      { key: 'refund-list', title: '退款处理', path: '/refund/list' },
      { key: 'inventory-record', title: '库存流水', path: '/inventory/record' },
    ],
  },
  {
    key: 'promotion',
    title: '营销',
    icon: 'Present',
    disabled: true,
    children: [
      { key: 'coupon-list', title: '优惠券', path: '/promotion/coupon', disabled: true },
      { key: 'seckill-list', title: '秒杀活动', path: '/promotion/seckill', disabled: true },
    ],
  },
  {
    key: 'data',
    title: '数据',
    icon: 'DataLine',
    children: [{ key: 'dashboard', title: '数据看板', path: '/dashboard' }],
  },
]

/** 菜单 key → 面包屑用的一级标题 */
export function findMenuTrail(path) {
  for (const group of MENU_TREE) {
    if (group.path === path) {
      return [{ title: group.title }]
    }
    if (group.children) {
      const child = group.children.find((item) => item.path === path)
      if (child) {
        return [{ title: group.title }, { title: child.title }]
      }
    }
  }
  return []
}
