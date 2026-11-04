<script setup>
/**
 * 工作台（M5）。指标卡接 `GET /api/admin/dashboard`，与数据看板同源，
 * 保证"工作台上看到的数字"和"看板里看到的数字"永远一致。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getDashboard } from '@/api/dashboard'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const shortcuts = [
  { title: '我的商品', desc: '上下架 / 编辑 / 删除', path: '/product/list', icon: 'Goods' },
  { title: '发布商品', desc: 'SPU + SKU + 初始库存', path: '/product/create', icon: 'Plus' },
  { title: '库存管理', desc: '调整库存并写流水', path: '/inventory/list', icon: 'Box' },
  { title: '订单管理', desc: '发货 / 查看详情', path: '/order/list', icon: 'Tickets' },
  { title: '退款处理', desc: '全额退款与状态跟踪', path: '/refund/list', icon: 'RefreshLeft' },
  { title: '分类与品牌', desc: '发布商品的前置字典', path: '/category/list', icon: 'Collection' },
]

const metrics = [
  { label: '库存 SKU', key: 'skuTotal' },
  { label: '待发货订单', key: 'toShip' },
  { label: '退款中', key: 'refunding' },
  { label: '今日 GMV', key: 'gmvToday' },
]

const roleText = computed(() => userStore.roles.join(' / ') || '-')

const loading = ref(false)
const overview = ref(null)
const skuTotal = ref(null)

const metricValues = computed(() => ({
  skuTotal: skuTotal.value ?? '—',
    toShip: overview.value?.toShip ?? 0,
    refunding: overview.value?.refunding ?? 0,
  gmvToday: overview.value
    ? `¥${Number(overview.value.gmvToday ?? 0).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}`
    : '—',
}))

onMounted(async () => {
  loading.value = true
  try {
    const data = await getDashboard({ days: 7 })
    overview.value = data.overview
    skuTotal.value = data.skuTotal ?? 0
  } catch {
    // 请求层已统一提示；工作台保持占位，不编造数字
  } finally {
    loading.value = false
  }
})
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
      <el-tag type="success" effect="plain">指标已接 /api/admin/dashboard</el-tag>
    </div>

    <div class="metric-row" v-loading="loading">
      <div
        v-for="m in metrics"
        :key="m.key"
        class="metric-card"
        :class="{ clickable: m.key === 'toShip' || m.key === 'refunding' }"
        @click="
          m.key === 'toShip'
            ? router.push({ path: '/order/list', query: { status: 20 } })
            : m.key === 'refunding'
              ? router.push({ path: '/refund/list', query: { status: 10 } })
              : null
        "
      >
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value">{{ metricValues[m.key] ?? '—' }}</div>
        <div class="text-muted text-xs">
          {{ m.key === 'skuTotal' ? '已有库存记录的 SKU' : '实时数据' }}
        </div>
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

.metric-card.clickable {
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.metric-card.clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.metric-label {
  color: var(--text-regular);
  font-size: 13px;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  margin: 6px 0 2px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
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
