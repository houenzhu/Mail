<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import { fetchOrders } from '@/api/order'
import { formatPrice } from '@/utils/format'
import { inferImageKey } from '@/utils/media'
import { messageForError } from '@/utils/errors'
import { ORDER_TABS, formatDateTime, formatSkuSpec, statusTone } from '@/utils/order'

const route = useRoute()
const router = useRouter()

const activeStatus = ref(normalizeStatus(route.query.status))
const orders = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const loading = ref(true)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const isEmpty = computed(() => !loading.value && !error.value && orders.value.length === 0)

function normalizeStatus(value) {
  const num = Number(Array.isArray(value) ? value[0] : value)
  return Number.isFinite(num) && num > 0 ? num : undefined
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchOrders({
      status: activeStatus.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    // 兼容返回结构：{ total, list } —— 与后端 PageResult 一致
    orders.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total) || 0
  } catch (cause) {
    error.value = messageForError(cause, '订单加载失败')
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  if (activeStatus.value === tab.status) return
  activeStatus.value = tab.status
  pageNo.value = 1
  router.replace({ name: 'order-list', query: tab.status ? { status: tab.status } : {} })
  load()
}

function goPage(next) {
  if (next < 1 || next > totalPages.value || next === pageNo.value) return
  pageNo.value = next
  load()
}

function itemCount(order) {
  return (order.items || []).reduce((sum, item) => sum + (Number(item.qty) || 0), 0)
}

watch(() => route.query.status, (value) => {
  const next = normalizeStatus(value)
  if (next === activeStatus.value) return
  activeStatus.value = next
  pageNo.value = 1
  load()
})

onMounted(load)
</script>

<template>
  <div class="orders-page">
    <SiteHeader />

    <main class="mall-container orders-main">
      <nav class="orders-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>我的订单</span>
      </nav>

      <header class="orders-head">
        <div>
          <h1>我的订单</h1>
          <p>共 {{ total }} 笔订单</p>
        </div>
        <RouterLink class="orders-head__account" :to="{ name: 'account' }">
          账户中心 <AppIcon name="arrow-right" :size="13" />
        </RouterLink>
      </header>

      <nav class="order-tabs" aria-label="订单状态筛选">
        <button
          v-for="tab in ORDER_TABS"
          :key="tab.label"
          type="button"
          class="order-tabs__item"
          :class="{ 'is-active': activeStatus === tab.status }"
          @click="switchTab(tab)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <section v-if="loading" class="orders-skeleton">
        <div v-for="i in 3" :key="i" class="skeleton-card" />
      </section>

      <section v-else-if="error" class="orders-state">
        <EmptyState icon="ticket" title="订单加载失败" :description="error">
          <BaseButton @click="load">重新加载</BaseButton>
        </EmptyState>
      </section>

      <section v-else-if="isEmpty" class="orders-state">
        <EmptyState icon="ticket" title="还没有相关订单" description="去挑点喜欢的好物吧。">
          <BaseButton :to="{ name: 'search', query: { q: '' } }">去逛逛</BaseButton>
        </EmptyState>
      </section>

      <ul v-else class="order-list">
        <li v-for="row in orders" :key="row.order.orderNo" class="order-card">
          <header class="order-card__head">
            <div class="order-card__meta">
              <span class="order-card__no">订单号 {{ row.order.orderNo }}</span>
              <span class="order-card__time">{{ formatDateTime(row.order.createdAt) }}</span>
            </div>
            <span class="order-card__status" :data-tone="statusTone(row.order.status)">
              {{ row.order.statusLabel }}
            </span>
          </header>

          <RouterLink
            class="order-card__body"
            :to="{ name: 'order-detail', params: { orderNo: row.order.orderNo } }"
          >
            <div class="order-card__items">
              <div v-for="item in row.items" :key="item.id" class="order-item">
                <SmartImage
                  class="order-item__media"
                  :src="item.image"
                  :alt="item.productName"
                  :fallback-key="inferImageKey(item.productName)"
                  ratio="1"
                />
                <div class="order-item__info">
                  <p class="order-item__name">{{ item.productName }}</p>
                  <p class="order-item__spec">{{ formatSkuSpec(item.skuSpec) }}</p>
                </div>
                <span class="order-item__qty">× {{ item.qty }}</span>
              </div>
            </div>

            <div class="order-card__amount">
              <span>共 {{ itemCount(row) }} 件</span>
              <strong>¥{{ formatPrice(row.order.payAmount) }}</strong>
            </div>
          </RouterLink>

          <footer class="order-card__actions">
            <BaseButton
              size="sm"
              variant="ghost"
              :to="{ name: 'order-detail', params: { orderNo: row.order.orderNo } }"
            >
              查看详情
            </BaseButton>
            <BaseButton
              v-if="Number(row.order.status) === 10"
              size="sm"
              :to="{ name: 'pay', params: { orderNo: row.order.orderNo } }"
            >
              去支付
            </BaseButton>
          </footer>
        </li>
      </ul>

      <nav v-if="totalPages > 1" class="pager" aria-label="分页">
        <button type="button" :disabled="pageNo <= 1" @click="goPage(pageNo - 1)">上一页</button>
        <span>{{ pageNo }} / {{ totalPages }}</span>
        <button type="button" :disabled="pageNo >= totalPages" @click="goPage(pageNo + 1)">下一页</button>
      </nav>
    </main>

    <SiteFooter />
  </div>
</template>

<style scoped>
.orders-page { display: flex; flex-direction: column; min-height: 100vh; }
.orders-main { flex: 1; padding-top: 18px; padding-bottom: 40px; }
.orders-breadcrumb { display: flex; align-items: center; gap: 6px; color: var(--mall-ink-3); font-size: 12px; }
.orders-breadcrumb a:hover { color: var(--mall-brand); }
.orders-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin: 14px 0 16px; }
.orders-head h1 { font-size: 24px; font-weight: 800; }
.orders-head p { margin-top: 4px; color: var(--mall-ink-3); font-size: 12px; }
.orders-head__account { display: inline-flex; align-items: center; gap: 3px; color: var(--mall-ink-2); font-size: 13px; }
.orders-head__account:hover { color: var(--mall-brand); }
.order-tabs { display: flex; gap: 4px; margin-bottom: 16px; padding: 4px; border-radius: 999px; background: #fff; width: fit-content; overflow-x: auto; }
.order-tabs__item { padding: 7px 16px; border-radius: 999px; color: var(--mall-ink-2); font-size: 13px; white-space: nowrap; }
.order-tabs__item:hover { color: var(--mall-brand); }
.order-tabs__item.is-active { background: var(--mall-ink); color: #fff; font-weight: 600; }
.order-list { display: flex; flex-direction: column; gap: 14px; }
.order-card { border-radius: var(--mall-radius); background: #fff; overflow: hidden; }
.order-card__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 13px 18px; border-bottom: 1px solid var(--mall-line); background: var(--mall-surface-2); }
.order-card__meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; min-width: 0; }
.order-card__no { color: var(--mall-ink-2); font-size: 12px; font-variant-numeric: tabular-nums; }
.order-card__time { color: var(--mall-ink-3); font-size: 12px; }
.order-card__status { flex-shrink: 0; font-size: 13px; font-weight: 700; }
.order-card__status[data-tone='warn'] { color: var(--mall-brand); }
.order-card__status[data-tone='brand'] { color: #1a56db; }
.order-card__status[data-tone='muted'] { color: var(--mall-ink-3); }
.order-card__body { display: flex; align-items: center; gap: 18px; padding: 16px 18px; }
.order-card__body:hover .order-item__name { color: var(--mall-brand); }
.order-card__items { display: flex; flex: 1; flex-direction: column; gap: 10px; min-width: 0; }
.order-item { display: flex; align-items: center; gap: 12px; min-width: 0; }
.order-item__media { width: 60px; height: 60px; border-radius: 8px; flex-shrink: 0; }
.order-item__info { flex: 1; min-width: 0; }
.order-item__name { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; transition: color 0.15s ease; }
.order-item__spec { margin-top: 3px; color: var(--mall-ink-3); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.order-item__qty { color: var(--mall-ink-3); font-size: 12px; }
.order-card__amount { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex-shrink: 0; }
.order-card__amount span { color: var(--mall-ink-3); font-size: 12px; }
.order-card__amount strong { font-size: 18px; font-weight: 800; }
.order-card__actions { display: flex; justify-content: flex-end; gap: 8px; padding: 0 18px 16px; }
.orders-skeleton { display: flex; flex-direction: column; gap: 14px; }
.skeleton-card { height: 168px; border-radius: var(--mall-radius); background: linear-gradient(90deg, #f2f2f2, #fafafa, #f2f2f2); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
.orders-state { padding: 20px; border-radius: var(--mall-radius); background: #fff; }
.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 20px; color: var(--mall-ink-2); font-size: 13px; }
.pager button { padding: 6px 14px; border: 1px solid var(--mall-line); border-radius: 8px; background: #fff; font-size: 13px; }
.pager button:disabled { color: var(--mall-ink-4); cursor: not-allowed; }
@media (max-width: 760px) {
  .orders-head h1 { font-size: 20px; }
  .order-card__body { align-items: flex-start; flex-direction: column; gap: 12px; }
  .order-card__items { width: 100%; }
  .order-card__amount { flex-direction: row; align-items: baseline; gap: 8px; }
  .order-item__media { width: 52px; height: 52px; }
}
</style>
