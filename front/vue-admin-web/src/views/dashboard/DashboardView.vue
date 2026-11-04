<script setup>
/**
 * 数据看板（UC-ADMIN-03 / M5）。
 *
 * 所有数字都来自 `GET /api/admin/dashboard`，口径写死在页面上，避免运营误读：
 *  - GMV 只含已支付及之后的订单，按 paid_at 归日；取消/待支付不计入。
 *  - 售罄率 = 售罄 SKU / 有库存记录的 SKU；分母为 0 时显示 "—"，不显示 0%。
 *  - 趋势按日补齐，没有成交的日期显示 0 而不是断开。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getDashboard } from '@/api/dashboard'

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const data = ref(null)
const days = ref(7)

const DAY_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
]

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    data.value = await getDashboard({ days: days.value })
  } catch (e) {
    loadError.value = e?.message || '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(days, load)

const overview = computed(() => data.value?.overview ?? {})
const trend = computed(() => data.value?.trend ?? [])

const money = (value) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const percent = (value) => {
  if (value == null) return '—'
  return `${(Number(value) * 100).toFixed(2)}%`
}

const cards = computed(() => [
  {
    key: 'gmvToday',
    label: '今日 GMV',
    value: money(overview.value.gmvToday),
    hint: '按支付时间归日，不含待支付与已取消',
    accent: 'orange',
  },
  {
    key: 'ordersToday',
    label: '今日订单',
    value: String(overview.value.ordersToday ?? 0),
    hint: '按创建时间归日，含未支付',
    accent: 'blue',
  },
  {
    key: 'toShip',
    label: '待发货',
    value: String(overview.value.toShip ?? 0),
    hint: '状态为已支付，可进入订单页发货',
    accent: 'teal',
    link: '/order/list',
    query: { status: 20 },
  },
  {
    key: 'refunding',
    label: '退款中',
    value: String(overview.value.refunding ?? 0),
    hint: '订单状态为退款中，需要跟进',
    accent: 'red',
    link: '/order/list',
    query: { status: 60 },
  },
])

/* ---------------- 趋势图（SVG，不引入图表库） ---------------- */

const CHART = { width: 720, height: 220, padLeft: 56, padRight: 16, padTop: 16, padBottom: 28 }

const maxGmv = computed(() => {
  const values = trend.value.map((p) => Number(p.gmv ?? 0))
  const max = Math.max(...values, 0)
  return max > 0 ? max : 1
})

const maxOrders = computed(() => {
  const values = trend.value.map((p) => Number(p.orders ?? 0))
  return Math.max(...values, 1)
})

const innerWidth = CHART.width - CHART.padLeft - CHART.padRight
const innerHeight = CHART.height - CHART.padTop - CHART.padBottom

function xOf(index) {
  if (trend.value.length <= 1) return CHART.padLeft + innerWidth / 2
  return CHART.padLeft + (innerWidth * index) / (trend.value.length - 1)
}

function yOfGmv(value) {
  return CHART.padTop + innerHeight - (Number(value ?? 0) / maxGmv.value) * innerHeight
}

function yOfOrders(value) {
  return CHART.padTop + innerHeight - (Number(value ?? 0) / maxOrders.value) * innerHeight
}

const gmvPath = computed(() =>
  trend.value.map((p, i) => `${i === 0 ? 'M' : 'L'}${xOf(i)},${yOfGmv(p.gmv)}`).join(' '),
)

const gmvAreaPath = computed(() => {
  if (!trend.value.length) return ''
  const base = CHART.padTop + innerHeight
  const last = trend.value.length - 1
  return `${gmvPath.value} L${xOf(last)},${base} L${xOf(0)},${base} Z`
})

const ordersPath = computed(() =>
  trend.value.map((p, i) => `${i === 0 ? 'M' : 'L'}${xOf(i)},${yOfOrders(p.orders)}`).join(' '),
)

const xTicks = computed(() => {
  const points = trend.value
  if (!points.length) return []
  // 最多 7 个刻度，避免 30 天时标签重叠
  const step = Math.max(1, Math.ceil(points.length / 7))
  return points
    .map((p, i) => ({ i, date: String(p.date).slice(5) }))
    .filter((t) => t.i % step === 0 || t.i === points.length - 1)
})

function onCardClick(card) {
  if (!card.link) return
  router.push({ path: card.link, query: card.query })
}

/* ---------------- 库存与待办 ---------------- */

const stockRows = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    { label: 'SKU 总数', value: d.skuTotal ?? 0, hint: '已有库存记录的 SKU' },
    { label: '售罄 SKU', value: d.skuSoldOut ?? 0, hint: 'total > 0 且可售为 0' },
    { label: '未备货 SKU', value: d.skuZeroStock ?? 0, hint: 'total = 0，单独计数' },
    { label: '低库存 SKU', value: d.lowStock ?? 0, hint: '可售 ≤ 预警阈值' },
  ]
})

const todos = computed(() => [
  {
    key: 'toShip',
    title: '待发货订单',
    count: overview.value.toShip ?? 0,
    path: '/order/list',
    query: { status: 20 },
    action: '去发货',
  },
  {
    key: 'refunding',
    title: '退款中订单',
    count: overview.value.refunding ?? 0,
    path: '/refund/list',
    query: { status: 10 },
    action: '查看退款',
  },
  {
    key: 'lowStock',
    title: '低库存 SKU',
    count: data.value?.lowStock ?? 0,
    path: '/inventory/list',
    query: {},
    action: '补货',
  },
])
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-toolbar">
      <div>
        <h2 class="page-title">数据看板</h2>
        <p class="text-muted text-xs">
          GMV / 订单量 / 售罄率 · 数据来自订单、支付与库存服务实时聚合
        </p>
      </div>
      <div class="toolbar-actions">
        <el-radio-group v-model="days" size="small">
          <el-radio-button v-for="opt in DAY_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
      description="看板的主指标依赖订单服务，失败时会明确报错而不是显示 0，避免误读。"
    />

    <template v-else>
      <div class="metric-row">
        <div
          v-for="card in cards"
          :key="card.key"
          class="metric-card"
          :class="[`accent-${card.accent}`, { clickable: !!card.link }]"
          @click="onCardClick(card)"
        >
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-hint">{{ card.hint }}</div>
        </div>
      </div>

      <div class="chart-grid">
        <div class="page-card chart-card">
          <div class="card-header">
            <h3 class="section-title">GMV 趋势</h3>
            <span class="text-muted text-xs">按支付时间归日 · 单位：元</span>
          </div>
          <svg
            v-if="trend.length"
            class="chart"
            :viewBox="`0 0 ${CHART.width} ${CHART.height}`"
            role="img"
            aria-label="GMV 趋势折线图"
          >
            <defs>
              <linearGradient id="gmvFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="var(--brand-primary)" stop-opacity="0.22" />
                <stop offset="100%" stop-color="var(--brand-primary)" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line
              v-for="i in 4"
              :key="`grid-${i}`"
              :x1="CHART.padLeft"
              :x2="CHART.width - CHART.padRight"
              :y1="CHART.padTop + (innerHeight * (i - 1)) / 3"
              :y2="CHART.padTop + (innerHeight * (i - 1)) / 3"
              class="grid-line"
            />
            <path :d="gmvAreaPath" fill="url(#gmvFill)" />
            <path :d="gmvPath" class="line-primary" />
            <g v-for="(p, i) in trend" :key="`gmv-dot-${p.date}`">
              <circle :cx="xOf(i)" :cy="yOfGmv(p.gmv)" r="3" class="dot-primary" />
            </g>
            <text
              v-for="tick in xTicks"
              :key="`x-${tick.date}-${tick.i}`"
              :x="xOf(tick.i)"
              :y="CHART.height - 8"
              class="axis-label"
              text-anchor="middle"
            >
              {{ tick.date }}
            </text>
            <text :x="8" :y="CHART.padTop + 4" class="axis-label">¥{{ maxGmv.toFixed(0) }}</text>
            <text :x="8" :y="CHART.padTop + innerHeight + 4" class="axis-label">¥0</text>
          </svg>
          <el-empty v-else description="暂无趋势数据" :image-size="72" />
        </div>

        <div class="page-card chart-card">
          <div class="card-header">
            <h3 class="section-title">订单量趋势</h3>
            <span class="text-muted text-xs">按下单时间归日 · 单位：笔</span>
          </div>
          <svg
            v-if="trend.length"
            class="chart"
            :viewBox="`0 0 ${CHART.width} ${CHART.height}`"
            role="img"
            aria-label="订单量趋势折线图"
          >
            <line
              v-for="i in 4"
              :key="`grid2-${i}`"
              :x1="CHART.padLeft"
              :x2="CHART.width - CHART.padRight"
              :y1="CHART.padTop + (innerHeight * (i - 1)) / 3"
              :y2="CHART.padTop + (innerHeight * (i - 1)) / 3"
              class="grid-line"
            />
            <path :d="ordersPath" class="line-secondary" />
            <g v-for="(p, i) in trend" :key="`ord-dot-${p.date}`">
              <circle :cx="xOf(i)" :cy="yOfOrders(p.orders)" r="3" class="dot-secondary" />
            </g>
            <text
              v-for="tick in xTicks"
              :key="`x2-${tick.date}-${tick.i}`"
              :x="xOf(tick.i)"
              :y="CHART.height - 8"
              class="axis-label"
              text-anchor="middle"
            >
              {{ tick.date }}
            </text>
            <text :x="8" :y="CHART.padTop + 4" class="axis-label">{{ maxOrders }}</text>
            <text :x="8" :y="CHART.padTop + innerHeight + 4" class="axis-label">0</text>
          </svg>
          <el-empty v-else description="暂无趋势数据" :image-size="72" />
        </div>
      </div>

      <div class="bottom-grid">
        <div class="page-card">
          <div class="card-header">
            <h3 class="section-title">库存与售罄</h3>
            <span class="text-muted text-xs">售罄率 {{ percent(data?.sellOutRate) }}</span>
          </div>
          <el-progress
            v-if="data?.sellOutRate != null"
            :percentage="Number((data.sellOutRate * 100).toFixed(2))"
            :stroke-width="8"
            :color="'var(--brand-primary)'"
          />
          <p v-else class="text-muted text-xs stock-empty">暂无库存记录，售罄率不适用（不显示 0%）</p>
          <div class="stock-row">
            <div v-for="row in stockRows" :key="row.label" class="stock-item">
              <div class="stock-value">{{ row.value }}</div>
              <div class="stock-label">{{ row.label }}</div>
              <div class="stock-hint">{{ row.hint }}</div>
            </div>
          </div>
        </div>

        <div class="page-card">
          <div class="card-header">
            <h3 class="section-title">待办</h3>
            <span class="text-muted text-xs">点击直接进入对应列表</span>
          </div>
          <div class="todo-list">
            <button
              v-for="todo in todos"
              :key="todo.key"
              type="button"
              class="todo-item"
              @click="router.push({ path: todo.path, query: todo.query })"
            >
              <span class="todo-title">{{ todo.title }}</span>
              <span class="todo-count" :class="{ zero: !todo.count }">{{ todo.count }}</span>
              <span class="todo-action">{{ todo.action }} →</span>
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.text-muted {
  color: var(--text-secondary);
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.metric-card {
  background: var(--bg-white);
  border: 1px solid var(--border-color-light);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-card);
  padding: 14px 16px;
  border-left: 3px solid var(--border-color);
}

.metric-card.clickable {
  cursor: pointer;
  transition: border-color 0.15s, transform 0.15s;
}

.metric-card.clickable:hover {
  transform: translateY(-1px);
}

.accent-orange {
  border-left-color: #ff8b3d;
}

.accent-blue {
  border-left-color: #409eff;
}

.accent-teal {
  border-left-color: #14b8a6;
}

.accent-red {
  border-left-color: #f56c6c;
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

.metric-hint {
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 16px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}

.chart-card {
  min-width: 0;
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.section-title {
  font-size: 14px;
  margin: 0;
}

.chart {
  width: 100%;
  height: 220px;
  display: block;
}

.grid-line {
  stroke: var(--border-color-light);
  stroke-width: 1;
}

.line-primary {
  fill: none;
  stroke: var(--brand-primary);
  stroke-width: 2;
  stroke-linejoin: round;
}

.dot-primary {
  fill: var(--bg-white);
  stroke: var(--brand-primary);
  stroke-width: 2;
}

.line-secondary {
  fill: none;
  stroke: #14b8a6;
  stroke-width: 2;
  stroke-linejoin: round;
}

.dot-secondary {
  fill: var(--bg-white);
  stroke: #14b8a6;
  stroke-width: 2;
}

.axis-label {
  fill: var(--text-secondary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 10px;
}

.stock-empty {
  margin: 4px 0 8px;
}

.stock-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.stock-item {
  min-width: 0;
}

.stock-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stock-label {
  font-size: 12px;
  color: var(--text-regular);
}

.stock-hint {
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 15px;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-white);
  border: 1px solid var(--border-color-light);
  border-radius: 4px;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: var(--text-primary);
  transition: border-color 0.15s, background-color 0.15s;
}

.todo-item:hover {
  border-color: var(--brand-primary);
  background: var(--brand-primary-light);
}

.todo-title {
  flex: 1 1 auto;
  font-size: 13px;
}

.todo-count {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #f56c6c;
}

.todo-count.zero {
  color: var(--text-placeholder);
}

.todo-action {
  font-size: 12px;
  color: var(--brand-primary);
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .metric-row,
  .stock-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid,
  .bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
