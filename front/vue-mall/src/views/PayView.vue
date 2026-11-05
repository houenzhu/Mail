<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppToast from '@/components/common/AppToast.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import { useRequestId } from '@/composables/useRequestId'
import { fetchOrderDetail } from '@/api/order'
import { createPayment, fetchPayment, mockPay } from '@/api/pay'
import { formatPrice } from '@/utils/format'
import { messageForError } from '@/utils/errors'
import { ORDER_STATUS, formatCountdown, remainingMs } from '@/utils/order'

const route = useRoute()
const router = useRouter()
const toastRef = ref(null)
const { requestId, renew } = useRequestId('pay')

const PAY_TYPE = {
  WECHAT: 1,
  ALIPAY: 2,
}

const loading = ref(true)
const error = ref('')
const order = ref(null)
const payment = ref(null)
const paying = ref(false)
const checking = ref(false)
const done = ref(false)
const now = ref(Date.now())
const payType = ref(PAY_TYPE.WECHAT)

let ticker = null
let pollTimer = null
let pollCount = 0

const orderNo = computed(() => String(route.params.orderNo || ''))
const isMock = computed(() => payment.value?.channel === 'MOCK')
const countdown = computed(() => formatCountdown(remainingMs(order.value?.expireAt, now.value)))
const expired = computed(() => remainingMs(order.value?.expireAt, now.value) <= 0)
const channelLabel = computed(() => (
  payment.value?.channel === 'MOCK' ? '模拟支付通道' : (payment.value?.channel || '在线支付')
))

async function loadOrder() {
  const detail = await fetchOrderDetail(orderNo.value)
  order.value = detail?.order || null
  if (order.value && Number(order.value.status) !== ORDER_STATUS.CREATED) {
    if ([20, 30, 40].includes(Number(order.value.status))) {
      done.value = true
    }
  }
}

async function startPayment() {
  if (!order.value || paying.value) return
  paying.value = true
  error.value = ''
  try {
    const created = await createPayment({
      orderNo: order.value.orderNo,
      payType: payType.value,
      requestId: requestId.value,
    })
    payment.value = created
    // 已成功（幂等重放）时直接回订单
    if (Number(created?.status) === 20) {
      finish()
    }
  } catch (cause) {
    error.value = messageForError(cause, '发起支付失败，请稍后重试')
  } finally {
    paying.value = false
  }
}

async function refreshPayment() {
  if (!payment.value?.paymentNo) return
  checking.value = true
  try {
    const latest = await fetchPayment(payment.value.paymentNo)
    payment.value = { ...payment.value, ...latest }
    if (Number(latest?.status) === 20) finish()
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '支付状态查询失败'), 'error')
  } finally {
    checking.value = false
  }
}

function startPolling() {
  stopPolling()
  pollCount = 0
  pollTimer = setInterval(async () => {
    pollCount += 1
    // 最多轮询 20 次（约 30 秒），之后交给用户手动刷新，避免后台一直打请求
    if (pollCount > 20 || done.value) {
      stopPolling()
      return
    }
    await refreshPayment()
  }, 1500)
}

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

async function simulatePay() {
  if (!payment.value?.paymentNo || checking.value) return
  checking.value = true
  try {
    const result = await mockPay(payment.value.paymentNo)
    // CallbackResultVO.orderStatus 可能为空：以支付单状态为准，再回查订单
    if (Number(result?.status) === 20) {
      toastRef.value?.show('支付成功')
      finish()
    } else {
      await refreshPayment()
    }
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '模拟支付失败'), 'error')
  } finally {
    checking.value = false
  }
}

function finish() {
  done.value = true
  stopPolling()
  renew()
  setTimeout(() => {
    router.replace({ name: 'order-detail', params: { orderNo: orderNo.value }, query: { paid: '1' } })
  }, 600)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    await loadOrder()
    if (!done.value) {
      await startPayment()
      startPolling()
    }
  } catch (cause) {
    error.value = messageForError(cause, '支付页加载失败')
  } finally {
    loading.value = false
  }
}

function changePayType(type) {
  if (payType.value === type || payment.value || paying.value) return
  payType.value = type
}

onMounted(() => {
  load()
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  clearInterval(ticker)
  stopPolling()
})
</script>

<template>
  <div class="pay-page">
    <SiteHeader />

    <main class="mall-container pay-main">
      <nav class="pay-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <RouterLink :to="{ name: 'order-list' }">我的订单</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>收银台</span>
      </nav>

      <section v-if="loading" class="pay-skeleton">
        <div class="skeleton-block" />
      </section>

      <section v-else-if="error && !order" class="pay-state">
        <EmptyState icon="shield" title="支付页暂时不可用" :description="error">
          <BaseButton @click="load">重新加载</BaseButton>
          <BaseButton variant="ghost" :to="{ name: 'order-list' }">返回订单列表</BaseButton>
        </EmptyState>
      </section>

      <section v-else-if="done" class="pay-result">
        <span class="pay-result__icon"><AppIcon name="check" :size="30" /></span>
        <h1>{{ order && Number(order.status) === ORDER_STATUS.CREATED ? '支付成功' : '订单已支付' }}</h1>
        <p>正在返回订单详情…</p>
        <BaseButton :to="{ name: 'order-detail', params: { orderNo } }">查看订单</BaseButton>
      </section>

      <div v-else-if="order" class="pay-layout">
        <section class="pay-panel">
          <header class="pay-panel__head">
            <div>
              <p class="pay-panel__eyebrow">收银台</p>
              <h1 class="pay-panel__amount">¥{{ formatPrice(order.payAmount) }}</h1>
            </div>
            <span class="pay-panel__badge" :class="{ 'is-mock': isMock }">
              {{ channelLabel }}
            </span>
          </header>

          <dl class="pay-info">
            <div>
              <dt>订单号</dt>
              <dd class="pay-info__mono">{{ order.orderNo }}</dd>
            </div>
            <div v-if="payment?.paymentNo">
              <dt>支付单号</dt>
              <dd class="pay-info__mono">{{ payment.paymentNo }}</dd>
            </div>
            <div>
              <dt>支付状态</dt>
              <dd>{{ payment?.statusLabel || '准备中' }}</dd>
            </div>
            <div>
              <dt>支付剩余时间</dt>
              <dd :class="{ 'pay-info__warn': expired }">
                {{ expired ? '已超时，订单将自动取消' : countdown }}
              </dd>
            </div>
          </dl>

          <div v-if="isMock" class="mock-note">
            <AppIcon name="shield-check" :size="16" />
            <div>
              <p class="mock-note__title">演示环境 · 模拟支付</p>
              <p class="mock-note__desc">
                当前接入的是 Mock 通道，点击下方按钮即可把支付与订单状态推进到「已支付」，
                不会产生真实扣款。
              </p>
            </div>
          </div>

          <div v-else class="pay-methods">
            <p class="pay-methods__label">选择支付方式</p>
            <div class="pay-methods__list">
              <button
                type="button"
                class="pay-method"
                :class="{ 'is-active': payType === PAY_TYPE.WECHAT }"
                @click="changePayType(PAY_TYPE.WECHAT)"
              >
                <span class="pay-method__icon"><AppIcon name="phone" :size="18" /></span>
                <span>微信支付</span>
              </button>
              <button
                type="button"
                class="pay-method"
                :class="{ 'is-active': payType === PAY_TYPE.ALIPAY }"
                @click="changePayType(PAY_TYPE.ALIPAY)"
              >
                <span class="pay-method__icon"><AppIcon name="monitor" :size="18" /></span>
                <span>支付宝</span>
              </button>
            </div>
          </div>

          <p v-if="error" class="pay-error" role="alert">{{ error }}</p>

          <div class="pay-actions">
            <BaseButton
              v-if="isMock"
              size="lg"
              :loading="checking"
              :disabled="expired"
              @click="simulatePay"
            >
              模拟支付成功
            </BaseButton>
            <BaseButton
              v-else
              size="lg"
              :loading="checking || paying"
              :disabled="expired"
              @click="refreshPayment"
            >
              我已完成支付
            </BaseButton>
            <BaseButton variant="ghost" :to="{ name: 'order-detail', params: { orderNo } }">
              稍后支付
            </BaseButton>
          </div>

          <p class="pay-actions__note">
            <AppIcon name="shield-check" :size="14" />
            支付结果以服务端支付单状态为准，本页会自动同步
          </p>
        </section>

        <aside class="pay-aside">
          <h2>订单摘要</h2>
          <p class="pay-aside__receiver">
            {{ order.receiverName }} · {{ order.receiverPhone }}
          </p>
          <p class="pay-aside__address">{{ order.receiverAddress }}</p>
          <RouterLink class="pay-aside__link" :to="{ name: 'order-detail', params: { orderNo } }">
            查看订单明细 <AppIcon name="arrow-right" :size="13" />
          </RouterLink>
        </aside>
      </div>
    </main>

    <SiteFooter />
    <AppToast ref="toastRef" />
  </div>
</template>

<style scoped>
.pay-page { display: flex; flex-direction: column; min-height: 100vh; }
.pay-main { flex: 1; padding-top: 18px; padding-bottom: 48px; }
.pay-breadcrumb { display: flex; align-items: center; gap: 6px; color: var(--mall-ink-3); font-size: 12px; }
.pay-breadcrumb a:hover { color: var(--mall-brand); }

.pay-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; align-items: start; margin-top: 14px; }
.pay-panel { padding: 28px 30px 26px; border-radius: var(--mall-radius-lg); background: #fff; box-shadow: var(--mall-shadow-sm); }
.pay-panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 20px; border-bottom: 1px solid var(--mall-line); }
.pay-panel__eyebrow { color: var(--mall-ink-3); font-size: 12px; letter-spacing: 0.08em; }
.pay-panel__amount { margin-top: 6px; font-size: 36px; font-weight: 800; letter-spacing: -0.01em; font-variant-numeric: tabular-nums; }
.pay-panel__badge { flex-shrink: 0; padding: 5px 12px; border-radius: 999px; background: var(--mall-surface-2); color: var(--mall-ink-2); font-size: 12px; }
.pay-panel__badge.is-mock { background: var(--mall-brand-soft); color: var(--mall-brand); font-weight: 600; }

.pay-info { display: flex; flex-direction: column; gap: 12px; padding: 20px 0; }
.pay-info > div { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; font-size: 13px; }
.pay-info dt { color: var(--mall-ink-3); flex-shrink: 0; }
.pay-info dd { color: var(--mall-ink); text-align: right; }
.pay-info__mono { font-variant-numeric: tabular-nums; font-size: 12px; word-break: break-all; }
.pay-info__warn { color: #c53030; font-weight: 600; }

.mock-note { display: flex; gap: 10px; padding: 14px 16px; border-radius: 12px; background: linear-gradient(120deg, #fff4ee, #fffaf7); border: 1px solid #ffe6d9; color: var(--mall-brand); }
.mock-note__title { font-size: 13px; font-weight: 700; }
.mock-note__desc { margin-top: 4px; color: var(--mall-ink-2); font-size: 12px; line-height: 1.6; }

.pay-methods { padding: 4px 0 18px; }
.pay-methods__label { color: var(--mall-ink-3); font-size: 12px; }
.pay-methods__list { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 10px; }
.pay-method { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border: 1px solid var(--mall-line); border-radius: 12px; background: #fff; font-size: 13px; text-align: left; transition: border-color 0.15s ease, box-shadow 0.15s ease; }
.pay-method:hover { border-color: var(--mall-line-strong); }
.pay-method.is-active { border-color: var(--mall-brand); box-shadow: 0 0 0 3px rgba(255, 80, 0, 0.08); }
.pay-method__icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; background: var(--mall-surface-2); color: var(--mall-ink-2); }
.pay-method.is-active .pay-method__icon { background: var(--mall-brand-soft); color: var(--mall-brand); }

.pay-error { margin: 4px 0 12px; color: #c53030; font-size: 12px; }
.pay-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding-top: 6px; }
.pay-actions__note { display: flex; align-items: center; gap: 5px; margin-top: 14px; color: var(--mall-ink-3); font-size: 12px; }

.pay-aside { padding: 20px 22px; border-radius: var(--mall-radius); background: #fff; box-shadow: var(--mall-shadow-sm); }
.pay-aside h2 { font-size: 15px; font-weight: 700; }
.pay-aside__receiver { margin-top: 12px; font-size: 13px; font-weight: 600; }
.pay-aside__address { margin-top: 4px; color: var(--mall-ink-2); font-size: 12px; line-height: 1.6; }
.pay-aside__link { display: inline-flex; align-items: center; gap: 3px; margin-top: 14px; color: var(--mall-brand); font-size: 12px; }

.pay-result { display: flex; flex-direction: column; align-items: center; gap: 10px; margin-top: 14px; padding: 56px 24px; border-radius: var(--mall-radius-lg); background: #fff; text-align: center; }
.pay-result__icon { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 50%; background: rgba(26, 156, 98, 0.1); color: var(--mall-success); }
.pay-result h1 { font-size: 20px; font-weight: 800; }
.pay-result p { color: var(--mall-ink-3); font-size: 13px; }

.pay-skeleton { margin-top: 14px; }
.skeleton-block { height: 320px; border-radius: var(--mall-radius-lg); background: linear-gradient(90deg, #f2f2f2, #fafafa, #f2f2f2); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
.pay-state { margin-top: 14px; padding: 20px; border-radius: var(--mall-radius); background: #fff; }

@media (max-width: 900px) {
  .pay-layout { grid-template-columns: 1fr; }
  .pay-aside { order: -1; }
}
@media (max-width: 760px) {
  .pay-panel { padding: 20px 18px; }
  .pay-panel__amount { font-size: 28px; }
  .pay-methods__list { grid-template-columns: 1fr; }
}
</style>
