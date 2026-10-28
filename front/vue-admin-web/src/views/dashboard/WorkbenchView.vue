<script setup>
/**
 * 工作台。指标卡数据依赖 `GET /api/admin/dashboard`（计划文档 G-10，尚未实现），
 * 因此当前明确标注为待接入，不用假数字充数。
 */
import { computed } from 'vue'

import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const shortcuts = [
  { title: '我的商品', desc: '上下架 / 编辑 / 删除', path: '/product/list', icon: 'Goods' },
  { title: '发布商品', desc: 'SPU + SKU + 初始库存', path: '/product/create', icon: 'Plus' },
  { title: '库存管理', desc: '调整库存并写流水', path: '/inventory/list', icon: 'Box' },
  { title: '订单管理', desc: '发货 / 查看详情', path: '/order/list', icon: 'Tickets' },
  { title: '退款处理', desc: '全额退款与状态跟踪', path: '/refund/list', icon: 'RefreshLeft' },
  { title: '分类与品牌', desc: '发布商品的前置字典', path: '/category/list', icon: 'Collection' },
]

const metrics = [
  { label: '在售商品', key: 'onSale' },
  { label: '待发货订单', key: 'toShip' },
  { label: '退款中', key: 'refunding' },
  { label: '今日 GMV', key: 'gmvToday' },
]

const roleText = computed(() => userStore.roles.join(' / ') || '-')
</script>

<template>
  <div class="page">
    <div class="page-card welcome">
      <div>
        <h2 class="page-title">你好，{{ userStore.displayName }}</h2>
        <p class="text-muted text-xs">
          当前角色：{{ roleText }} · 时间以服务端（Asia/Shanghai）为准
        </p>
      </div>
      <el-tag type="warning" effect="plain">指标接口 /api/admin/dashboard 待建</el-tag>
    </div>

    <div class="metric-row">
      <div v-for="m in metrics" :key="m.key" class="metric-card">
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value">--</div>
        <div class="text-muted text-xs">待接入指标接口</div>
      </div>
    </div>

    <div class="page-card">
      <h3 class="section-title">快捷入口</h3>
      <div class="shortcut-grid">
        <RouterLink v-for="s in shortcuts" :key="s.path" :to="s.path" class="shortcut">
          <el-icon class="shortcut-icon"><component :is="s.icon" /></el-icon>
          <div>
            <div class="shortcut-title">{{ s.title }}</div>
            <div class="text-muted text-xs">{{ s.desc }}</div>
          </div>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 8px 0;
}

.metric-card {
  background: var(--bg-white);
  border-radius: var(--card-radius);
  padding: 14px 16px;
}

.metric-label {
  color: var(--text-regular);
  font-size: 13px;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  margin: 6px 0 2px;
  color: var(--text-placeholder);
}

.section-title {
  font-size: 14px;
  margin: 0 0 12px;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.shortcut {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border-color-light);
  border-radius: 4px;
  color: var(--text-primary);
  transition: all 0.15s;
}

.shortcut:hover {
  border-color: var(--brand-primary);
  background: var(--brand-primary-light);
}

.shortcut-icon {
  font-size: 20px;
  color: var(--brand-primary);
}

.shortcut-title {
  font-size: 13px;
}

@media (max-width: 1100px) {
  .metric-row,
  .shortcut-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
