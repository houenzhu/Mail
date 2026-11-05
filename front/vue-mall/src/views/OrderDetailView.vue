<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppToast from '@/components/common/AppToast.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import { cancelOrder, confirmOrder, fetchOrderDetail } from '@/api/order'
import { formatPrice } from '@/utils/format'
import { inferImageKey } from '@/utils/media'
import { messageForError } from '@/utils/errors'
import {
  ORDER_STATUS,
  canCancel,
  canConfirm,
  canPay,
  formatCountdown,
  formatDateTime,
  formatSkuSpec,
  remainingMs,
  statusTone,
} from '@/utils/order'

const route = useRoute()
const router = useRouter()
const toastRef = ref(null)

const loading = ref(true)
const error = ref('')
const detail = ref(null)
const acting = ref(false)
const cancelOpen = ref(false)
const cancelReason = ref('')
const now = ref(Date.now())
const paying = ref(false)

let ticker = null

const orderNo = computed(() => String(route.params.orderNo || ''))
const order = computed(() => detail.value?.order || null)
const items = computed(() => detail.value?.items || [])
const logs = computed(() => {
  const list = detail.value?.logs || []
  return [...list].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
})
const itemCount = computed(() => items.value.reduce((sum, item) => sum + (Number(item.qty) || 0), 0))
const payCountdown = computed(() => formatCountdown(remainingMs(order.value?.expireAt, now.value)))
const payExpired = computed(() => canPay(order.value) && remainingMs(order.value?.expireAt, now.value) <= 0)

async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    detail.value = await fetchOrderDetail(orderNo.value)
  } catch (cause) {
    error.value = messageForError(cause, '订单加载失败')
  } finally {
    loading.value = false
  }
}

function goPay() {
  if (!order.value || paying.value) return
  paying.value = true
  router.push({ name: 'pay', params: { orderNo: order.value.orderNo } })
}

function openCancel() {
  cancelReason.value = ''
  cancelOpen.value = true
}

async function submitCancel() {
  if (!order.value || acting.value) return
  acting.value = true
  try {
    await cancelOrder(order.value.orderNo, cancelReason.value.trim() || undefined)
    cancelOpen.value = false
    toastRef.value?.show('订单已取消')
    await load({ silent: true })
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '取消失败，请稍后重试'), 'error')
  } finally {
    acting.value = false
  }
}

async function submitConfirm() {
  if (!order.value || acting.value) return
  acting.value = true
  try {
    await confirmOrder(order.value.orderNo)
    toastRef.value?.show('已确认收货')
    await load({ silent: true })
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '确认收货失败'), 'error')
  } finally {
    acting.value = false
  }
}

function operatorLabel(operator) {
  return { user: '买家', system: '系统', pay: '支付', mq: '消息', admin: '商家' }[operator] || '系统'
}

onMounted(() => {
  load()
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => clearInterval(ticker))
</script>

<template>
  <div class="detail-page">
    <SiteHeader />

    <main class="mall-container detail-main">
      <nav class="detail-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <RouterLink :to="{ name: 'order-list' }">我的订单</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>订单详情</span>
      </nav>

      <section v-if="loading" class="detail-skeleton">
        <div class="skeleton-block skeleton-block--hero" />
        <div class="skeleton-block" />
        <div class="skeleton-block" />
      </section>

      <section v-else-if="error" class="detail-state">
        <EmptyState icon="ticket" title="订单加载失败" :description="error">
          <BaseButton @click="load()">重新加载</BaseButton>
          <BaseButton variant="ghost" :to="{ name: 'order-list' }">返回订单列表</BaseButton>
        </EmptyState>
      </section>

      <template v-else-if="order">
        <section class="status-hero" :data-tone="statusTone(order.status)">
          <div class="status-hero__main">
            <p class="status-hero__label">{{ order.statusLabel }}</p>
            <p class="status-hero__hint">
              <template v-if="canPay(order)">
                <template v-if="payExpired">订单已超时，系统将自动取消</template>
                <template v-else>
                  请在 <strong>{{ payCountdown }}</strong> 内完成支付，超时订单自动取消
                </template>
              </template>
              <template v-else-if="Number(order.status) === ORDER_STATUS.PAID">
                商家正在备货，发货后会同步物流信息
              </template>
              <template v-else-if="Number(order.status) === ORDER_STATUS.SHIPPED">
                包裹已发出，收到商品后请确认收货
              </template>
              <template v-else-if="Number(order.status) === ORDER_STATUS.COMPLETED">
                订单已完成，感谢你的购买
              </template>
              <template v-else-if="Number(order.status) === ORDER_STATUS.CANCELLED">
                订单已取消，库存已回滚
              </template>
              <template v-else>订单状态已更新</template>
            </p>
          </div>

          <div class="status-hero__actions">
            <template v-if="canPay(order)">
              <BaseButton :disabled="payExpired || paying" :loading="paying" @click="goPay">
                去支付
              </BaseButton>
              <BaseButton variant="ghost" :loading="acting" @click="openCancel">取消订单</BaseButton>
            </template>
            <BaseButton
              v-else-if="canConfirm(order)"
              :loading="acting"
              @click="submitConfirm"
            >
              确认收货
            </BaseButton>
            <BaseButton v-else variant="ghost" :to="{ name: 'order-list' }">返回订单列表</BaseButton>
          </div>
        </section>

        <div class="detail-layout">
          <div class="detail-content">
            <section class="panel">
              <header class="panel__head">
                <h2><AppIcon name="map" :size="16" /> 收货信息</h2>
              </header>
              <div class="receiver">
                <p class="receiver__who">
                  <strong>{{ order.receiverName }}</strong>
                  <span>{{ order.receiverPhone }}</span>
                </p>
                <p class="receiver__address">{{ order.receiverAddress }}</p>
                <p v-if="order.remark" class="receiver__remark">备注：{{ order.remark }}</p>
              </div>
            </section>

            <section class="panel">
              <header class="panel__head">
                <h2><AppIcon name="truck" :size="16" /> 物流信息</h2>
              </header>
              <dl v-if="order.shippingCompany || order.trackingNo" class="info-list">
                <div>
                  <dt>物流公司</dt>
                  <dd>{{ order.shippingCompany || '—' }}</dd>
                </div>
                <div>
                  <dt>物流单号</dt>
                  <dd class="info-list__mono">{{ order.trackingNo || '—' }}</dd>
                </div>
                <div>
                  <dt>发货时间</dt>
                  <dd>{{ formatDateTime(order.shippedAt) }}</dd>
                </div>
              </dl>
              <p v-else class="panel__empty">商家尚未发货，发货后可在这里查看物流单号。</p>
            </section>

            <section class="panel">
              <header class="panel__head">
                <h2><AppIcon name="cart" :size="16" /> 商品清单</h2>
                <span class="panel__meta">共 {{ itemCount }} 件</span>
              </header>
              <ul class="item-list">
                <li v-for="item in items" :key="item.id" class="item">
                  <SmartImage
                    class="item__media"
                    :src="item.image"
                    :alt="item.productName"
                    :fallback-key="inferImageKey(item.productName)"
                    ratio="1"
                  />
                  <div class="item__info">
                    <RouterLink
                      v-if="item.productId"
                      class="item__name"
                      :to="{ name: 'product-detail', params: { id: item.productId } }"
                    >
                      {{ item.productName }}
                    </RouterLink>
                    <p v-else class="item__name">{{ item.productName }}</p>
                    <p class="item__spec">{{ formatSkuSpec(item.skuSpec) }}</p>
                  </div>
                  <div class="item__price">
                    <strong>¥{{ formatPrice(item.price) }}</strong>
                    <span>× {{ item.qty }}</span>
                  </div>
                </li>
              </ul>
            </section>

            <section class="panel">
              <header class="panel__head">
                <h2><AppIcon name="clock" :size="16" /> 状态流转</h2>
              </header>
              <ol class="timeline">
                <li v-for="(log, index) in logs" :key="index" class="timeline__item">
                  <span class="timeline__dot" :class="{ 'is-last': index === logs.length - 1 }" />
                  <div class="timeline__body">
                    <p class="timeline__title">
                      {{ log.toStatus === 0 ? '订单创建' : (log.remark || '状态更新') }}
                      <span class="timeline__operator">{{ operatorLabel(log.operator) }}</span>
                    </p>
                    <p class="timeline__time">{{ formatDateTime(log.createdAt) }}</p>
                  </div>
                </li>
              </ol>
            </section>
          </div>

          <aside class="detail-aside">
            <section class="panel">
              <header class="panel__head">
                <h2>订单信息</h2>
              </header>
              <dl class="info-list">
                <div>
                  <dt>订单号</dt>
                  <dd class="info-list__mono">{{ order.orderNo }}</dd>
                </div>
                <div>
                  <dt>下单时间</dt>
                  <dd>{{ formatDateTime(order.createdAt) }}</dd>
                </div>
                <div v-if="order.paidAt">
                  <dt>支付时间</dt>
                  <dd>{{ formatDateTime(order.paidAt) }}</dd>
                </div>
                <div v-if="order.completedAt">
                  <dt>完成时间</dt>
                  <dd>{{ formatDateTime(order.completedAt) }}</dd>
                </div>
                <div v-if="order.cancelledAt">
                  <dt>取消时间</dt>
                  <dd>{{ formatDateTime(order.cancelledAt) }}</dd>
                </div>
                <div v-if="order.cancelReason">
                  <dt>取消原因</dt>
                  <dd>{{ order.cancelReason }}</dd>
                </div>
              </dl>
            </section>

            <section class="panel">
              <header class="panel__head">
                <h2>金额明细</h2>
              </header>
              <dl class="amount-list">
                <div>
                  <dt>商品总额</dt>
                  <dd>¥{{ formatPrice(order.totalAmount) }}</dd>
                </div>
                <div v-if="Number(order.discountAmount) > 0">
                  <dt>优惠</dt>
                  <dd class="amount-list__discount">-¥{{ formatPrice(order.discountAmount) }}</dd>
                </div>
                <div>
                  <dt>运费</dt>
                  <dd>包邮</dd>
                </div>
                <div class="amount-list__total">
                  <dt>实付金额</dt>
                  <dd>¥{{ formatPrice(order.payAmount) }}</dd>
                </div>
              </dl>
            </section>
          </aside>
        </div>
      </template>
    </main>

    <SiteFooter />
    <AppToast ref="toastRef" />

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="cancelOpen" class="modal-layer">
          <button class="modal-mask" type="button" aria-label="关闭" @click="cancelOpen = false" />
          <section class="modal" role="dialog" aria-modal="true" aria-labelledby="cancel-title">
            <h2 id="cancel-title">取消订单</h2>
            <p class="modal__hint">取消后库存会立即回滚，该操作不可撤销。</p>
            <textarea
              v-model="cancelReason"
              class="modal__textarea"
              maxlength="120"
              rows="3"
              placeholder="选填，告诉我们取消的原因"
            />
            <div class="modal__actions">
              <BaseButton variant="ghost" :disabled="acting" @click="cancelOpen = false">再想想</BaseButton>
              <BaseButton :loading="acting" @click="submitCancel">确认取消</BaseButton>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; min-height: 100vh; }
.detail-main { flex: 1; padding-top: 18px; padding-bottom: 48px; }
.detail-breadcrumb { display: flex; align-items: center; gap: 6px; color: var(--mall-ink-3); font-size: 12px; }
.detail-breadcrumb a:hover { color: var(--mall-brand); }

.status-hero {
  display: flex; align-items: center; justify-content: space-between; gap: 20px; flex-wrap: wrap;
  margin: 14px 0 16px; padding: 24px 26px; border-radius: var(--mall-radius-lg);
  background: linear-gradient(120deg, #fff4ee, #fffaf7); border: 1px solid #ffe6d9;
}
.status-hero__label { font-size: 24px; font-weight: 800; color: var(--mall-brand); }
.status-hero__hint { margin-top: 6px; color: var(--mall-ink-2); font-size: 13px; }
.status-hero__hint strong { color: var(--mall-brand); font-variant-numeric: tabular-nums; }
.status-hero__actions { display: flex; gap: 10px; flex-wrap: wrap; }
.status-hero[data-tone='brand'] { background: linear-gradient(120deg, #eef4ff, #f8fbff); border-color: #dbe8ff; }
.status-hero[data-tone='brand'] .status-hero__label { color: #1a56db; }
.status-hero[data-tone='muted'] { background: var(--mall-surface-2); border-color: var(--mall-line); }
.status-hero[data-tone='muted'] .status-hero__label { color: var(--mall-ink-2); }

.detail-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
.detail-content, .detail-aside { display: flex; flex-direction: column; gap: 16px; }
.panel { padding: 20px 22px; border-radius: var(--mall-radius); background: #fff; box-shadow: var(--mall-shadow-sm); }
.panel__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--mall-line); }
.panel__head h2 { display: inline-flex; align-items: center; gap: 7px; font-size: 15px; font-weight: 700; }
.panel__meta { color: var(--mall-ink-3); font-size: 12px; }
.panel__empty { padding-top: 14px; color: var(--mall-ink-3); font-size: 13px; }

.receiver { padding-top: 14px; }
.receiver__who { display: flex; align-items: baseline; gap: 12px; font-size: 15px; }
.receiver__who strong { font-weight: 700; }
.receiver__who span { color: var(--mall-ink-2); font-size: 13px; font-variant-numeric: tabular-nums; }
.receiver__address { margin-top: 6px; color: var(--mall-ink-2); font-size: 13px; line-height: 1.6; }
.receiver__remark { margin-top: 8px; padding: 8px 10px; border-radius: 8px; background: var(--mall-surface-2); color: var(--mall-ink-2); font-size: 12px; }

.info-list { display: flex; flex-direction: column; gap: 10px; padding-top: 14px; }
.info-list > div { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; font-size: 13px; }
.info-list dt { flex-shrink: 0; color: var(--mall-ink-3); }
.info-list dd { color: var(--mall-ink); text-align: right; word-break: break-all; }
.info-list__mono { font-variant-numeric: tabular-nums; font-size: 12px; }

.item-list { display: flex; flex-direction: column; gap: 14px; padding-top: 16px; }
.item { display: flex; align-items: center; gap: 14px; }
.item__media { width: 64px; height: 64px; border-radius: 10px; flex-shrink: 0; }
.item__info { flex: 1; min-width: 0; }
.item__name { display: block; font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
a.item__name:hover { color: var(--mall-brand); }
.item__spec { margin-top: 3px; color: var(--mall-ink-3); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item__price { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; flex-shrink: 0; }
.item__price strong { font-size: 14px; font-variant-numeric: tabular-nums; }
.item__price span { color: var(--mall-ink-3); font-size: 12px; }

.timeline { padding: 16px 0 0; }
.timeline__item { position: relative; display: flex; gap: 12px; padding-bottom: 16px; }
.timeline__item:last-child { padding-bottom: 0; }
.timeline__dot { position: relative; flex-shrink: 0; width: 9px; height: 9px; margin-top: 5px; border-radius: 50%; background: var(--mall-line-strong); }
.timeline__dot.is-last { background: var(--mall-brand); box-shadow: 0 0 0 4px rgba(255, 80, 0, 0.12); }
.timeline__item:not(:last-child)::before {
  content: ''; position: absolute; left: 4px; top: 14px; bottom: 0; width: 1px; background: var(--mall-line);
}
.timeline__body { min-width: 0; }
.timeline__title { font-size: 13px; font-weight: 600; }
.timeline__operator { margin-left: 8px; padding: 1px 7px; border-radius: 999px; background: var(--mall-surface-2); color: var(--mall-ink-3); font-size: 11px; font-weight: 500; }
.timeline__time { margin-top: 3px; color: var(--mall-ink-3); font-size: 12px; font-variant-numeric: tabular-nums; }

.amount-list { display: flex; flex-direction: column; gap: 10px; padding-top: 14px; }
.amount-list > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; font-size: 13px; color: var(--mall-ink-2); }
.amount-list dd { font-variant-numeric: tabular-nums; }
.amount-list__discount { color: var(--mall-brand); }
.amount-list__total { padding-top: 12px; border-top: 1px dashed var(--mall-line-strong); }
.amount-list__total dt { color: var(--mall-ink); font-weight: 600; }
.amount-list__total dd { color: var(--mall-brand); font-size: 20px; font-weight: 800; }

.detail-skeleton { display: flex; flex-direction: column; gap: 16px; margin-top: 14px; }
.skeleton-block { height: 160px; border-radius: var(--mall-radius); background: linear-gradient(90deg, #f2f2f2, #fafafa, #f2f2f2); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
.skeleton-block--hero { height: 112px; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
.detail-state { margin-top: 14px; padding: 20px; border-radius: var(--mall-radius); background: #fff; }

.modal-layer { position: fixed; inset: 0; z-index: 110; display: grid; place-items: center; padding: 16px; }
.modal-mask { position: absolute; inset: 0; background: rgba(20, 20, 20, 0.38); backdrop-filter: blur(2px); }
.modal { position: relative; width: min(420px, 100%); padding: 22px 24px; border-radius: 16px; background: #fff; box-shadow: var(--mall-shadow-lg); }
.modal h2 { font-size: 17px; font-weight: 800; }
.modal__hint { margin-top: 6px; color: var(--mall-ink-3); font-size: 12px; }
.modal__textarea { width: 100%; margin-top: 14px; padding: 10px 12px; border: 1px solid var(--mall-line); border-radius: 10px; font: inherit; resize: vertical; }
.modal__textarea:focus { outline: none; border-color: var(--mall-brand); }
.modal__actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.16s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .detail-layout { grid-template-columns: 1fr; }
  .detail-aside { order: -1; }
}
@media (max-width: 760px) {
  .status-hero { padding: 18px; }
  .status-hero__label { font-size: 20px; }
  .panel { padding: 16px; }
  .item__media { width: 54px; height: 54px; }
}
</style>
