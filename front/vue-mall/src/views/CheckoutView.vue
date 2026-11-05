<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppToast from '@/components/common/AppToast.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import AddressFormSheet from '@/components/address/AddressFormSheet.vue'
import { useCartStore } from '@/stores/cart'
import { useRequestId } from '@/composables/useRequestId'
import * as addressApi from '@/api/address'
import { createOrder } from '@/api/order'
import { formatPrice } from '@/utils/format'
import { inferImageKey } from '@/utils/media'
import { messageForError } from '@/utils/errors'

const router = useRouter()
const cartStore = useCartStore()
const toastRef = ref(null)
const { requestId, renew } = useRequestId('order')

const loading = ref(true)
const loadError = ref('')
const addresses = ref([])
const selectedAddressId = ref(null)
const checkoutItems = ref([])
const totalQuantity = ref(0)
const payAmount = ref(0)
const remark = ref('')
const submitting = ref(false)

const formOpen = ref(false)
const editingAddress = ref(null)
const savingAddress = ref(false)
const addressSheetRef = ref(null)

const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === selectedAddressId.value) || null,
)
const hasItems = computed(() => checkoutItems.value.length > 0)
const canSubmit = computed(() => hasItems.value && Boolean(selectedAddress.value) && !submitting.value)

async function loadAddresses(preferId) {
  const list = await addressApi.fetchAddresses()
  addresses.value = Array.isArray(list) ? list : []
  const target = preferId && addresses.value.some((item) => item.id === preferId)
    ? preferId
    : (addresses.value.find((item) => item.isDefault) || addresses.value[0])?.id || null
  selectedAddressId.value = target
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // 进页重新拉结算预览：购物车价格 / 失效状态在上一页可能已经变化，
    // 不能复用抽屉里的旧数据，否则提交金额与用户看到的不一致。
    const [checkout] = await Promise.all([cartStore.checkout(), loadAddresses()])
    checkoutItems.value = (checkout?.items || []).map((item) => ({
      ...item,
      image: cartStore.items.find((row) => row.skuId === item.skuId)?.image || '',
    }))
    totalQuantity.value = Number(checkout?.totalQuantity) || 0
    payAmount.value = Number(checkout?.payAmount) || 0
  } catch (cause) {
    loadError.value = messageForError(cause, '结算信息加载失败')
  } finally {
    loading.value = false
  }
}

function openCreateAddress() {
  editingAddress.value = null
  formOpen.value = true
}

function openEditAddress(address) {
  editingAddress.value = address
  formOpen.value = true
}

async function saveAddress(payload) {
  savingAddress.value = true
  try {
    const saved = editingAddress.value
      ? await addressApi.updateAddress(editingAddress.value.id, payload)
      : await addressApi.createAddress(payload)
    formOpen.value = false
    await loadAddresses(saved?.id)
    toastRef.value?.show(editingAddress.value ? '地址已更新' : '地址已保存')
    editingAddress.value = null
  } catch (cause) {
    addressSheetRef.value?.setError(messageForError(cause, '地址保存失败'))
  } finally {
    savingAddress.value = false
  }
}

async function submit() {
  if (!canSubmit.value) return
  const address = selectedAddress.value
  submitting.value = true
  try {
    const created = await createOrder({
      requestId: requestId.value,
      receiverName: address.receiverName,
      receiverPhone: address.receiverPhone,
      receiverAddress: address.fullAddress,
      remark: remark.value.trim() || undefined,
      items: checkoutItems.value.map((item) => ({ skuId: item.skuId, qty: item.quantity })),
    })
    // 订单成功后才清理已购 SKU；失败时购物车保持原样，用户可以改数量重试
    try {
      await cartStore.removePurchased(checkoutItems.value.map((item) => item.skuId))
    } catch {
      // 清理失败不影响订单成立，购物车下次刷新会以后端为准
    }
    renew()
    toastRef.value?.show(created?.idempotentReplay ? '订单已存在，正在打开' : '下单成功')
    await router.replace({ name: 'order-detail', params: { orderNo: created.orderNo } })
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '下单失败，请稍后重试'), 'error')
    // 幂等号保留：网络超时时用户重试应命中同一笔订单，而不是再下一单
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="checkout-page">
    <SiteHeader />

    <main class="mall-container checkout-main">
      <nav class="checkout-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'cart' }">购物车</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>确认订单</span>
      </nav>

      <h1 class="checkout-title">确认订单</h1>

      <section v-if="loading" class="checkout-skeleton">
        <div class="skeleton-block" />
        <div class="skeleton-block" />
      </section>

      <section v-else-if="loadError" class="checkout-error">
        <EmptyState icon="truck" title="结算信息暂时不可用" :description="loadError">
          <BaseButton @click="load">重新加载</BaseButton>
          <BaseButton variant="ghost" :to="{ name: 'cart' }">返回购物车</BaseButton>
        </EmptyState>
      </section>

      <section v-else-if="!hasItems" class="checkout-error">
        <EmptyState icon="cart" title="没有可结算的商品" description="请回到购物车勾选要购买的商品。">
          <BaseButton :to="{ name: 'cart' }">返回购物车</BaseButton>
        </EmptyState>
      </section>

      <template v-else>
        <div class="checkout-layout">
          <div class="checkout-content">
            <section class="panel">
              <header class="panel__head">
                <h2>收货地址</h2>
                <button type="button" class="panel__action" @click="openCreateAddress">
                  <AppIcon name="grid" :size="14" /> 新增地址
                </button>
              </header>

              <div v-if="addresses.length" class="address-list">
                <label
                  v-for="address in addresses"
                  :key="address.id"
                  class="address-card"
                  :class="{ 'is-selected': address.id === selectedAddressId }"
                >
                  <input v-model="selectedAddressId" type="radio" :value="address.id" name="address" />
                  <div class="address-card__main">
                    <p class="address-card__who">
                      <strong>{{ address.receiverName }}</strong>
                      <span>{{ address.receiverPhone }}</span>
                      <span v-if="address.isDefault" class="address-card__badge">默认</span>
                    </p>
                    <p class="address-card__detail">{{ address.fullAddress }}</p>
                  </div>
                  <button
                    type="button"
                    class="address-card__edit"
                    @click.prevent="openEditAddress(address)"
                  >
                    编辑
                  </button>
                </label>
              </div>

              <p v-else class="address-empty">
                还没有收货地址，请先新增一条。
                <button type="button" class="address-empty__link" @click="openCreateAddress">立即新增</button>
              </p>
            </section>

            <section class="panel">
              <header class="panel__head">
                <h2>商品清单</h2>
                <span class="panel__meta">共 {{ totalQuantity }} 件</span>
              </header>

              <ul class="item-list">
                <li v-for="item in checkoutItems" :key="item.skuId" class="item">
                  <SmartImage
                    class="item__media"
                    :src="item.image"
                    :alt="item.productName"
                    :fallback-key="inferImageKey(item.productName)"
                    ratio="1"
                  />
                  <div class="item__info">
                    <p class="item__name">{{ item.productName }}</p>
                    <p class="item__spec">{{ item.skuName }}</p>
                  </div>
                  <div class="item__price">
                    <strong>¥{{ formatPrice(item.price) }}</strong>
                    <span>× {{ item.quantity }}</span>
                  </div>
                </li>
              </ul>
            </section>

            <section class="panel">
              <header class="panel__head">
                <h2>订单备注</h2>
              </header>
              <textarea
                v-model="remark"
                class="remark"
                maxlength="255"
                rows="3"
                placeholder="选填，如对配送时间的要求"
              />
            </section>
          </div>

          <aside class="summary">
            <h2>订单金额</h2>
            <dl class="summary__list">
              <div>
                <dt>商品件数</dt>
                <dd>{{ totalQuantity }} 件</dd>
              </div>
              <div>
                <dt>商品金额</dt>
                <dd>¥{{ formatPrice(payAmount) }}</dd>
              </div>
              <div>
                <dt>运费</dt>
                <dd class="summary__free">包邮</dd>
              </div>
              <div class="summary__total">
                <dt>应付金额</dt>
                <dd>¥{{ formatPrice(payAmount) }}</dd>
              </div>
            </dl>

            <p v-if="!selectedAddress" class="summary__hint">请先选择收货地址</p>

            <BaseButton class="summary__submit" size="lg" :loading="submitting" :disabled="!canSubmit" @click="submit">
              提交订单
            </BaseButton>
            <p class="summary__note">
              <AppIcon name="shield-check" :size="14" />
              提交后请在 30 分钟内完成支付，超时订单将自动取消
            </p>
          </aside>
        </div>
      </template>
    </main>

    <SiteFooter />
    <AppToast ref="toastRef" />
    <AddressFormSheet
      ref="addressSheetRef"
      :open="formOpen"
      :address="editingAddress"
      :saving="savingAddress"
      @close="formOpen = false"
      @save="saveAddress"
    />
  </div>
</template>

<style scoped>
.checkout-page { display: flex; flex-direction: column; min-height: 100vh; }
.checkout-main { flex: 1; padding-top: 18px; padding-bottom: 40px; }
.checkout-breadcrumb { display: flex; align-items: center; gap: 6px; color: var(--mall-ink-3); font-size: 12px; }
.checkout-breadcrumb a:hover { color: var(--mall-brand); }
.checkout-title { margin: 12px 0 18px; font-size: 24px; font-weight: 800; }
.checkout-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 20px; align-items: start; }
.checkout-content { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.panel { padding: 18px 20px; border-radius: var(--mall-radius); background: #fff; }
.panel__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel__head h2 { font-size: 16px; font-weight: 800; }
.panel__meta { color: var(--mall-ink-3); font-size: 12px; }
.panel__action { display: inline-flex; align-items: center; gap: 4px; color: var(--mall-brand); font-size: 13px; }
.address-list { display: flex; flex-direction: column; gap: 10px; }
.address-card {
  display: flex; align-items: flex-start; gap: 10px; padding: 13px 14px;
  border: 1px solid var(--mall-line); border-radius: 10px; cursor: pointer; transition: border-color 0.15s ease;
}
.address-card:hover { border-color: var(--mall-line-strong); }
.address-card.is-selected { border-color: var(--mall-brand); background: var(--mall-brand-soft); }
.address-card input { margin-top: 3px; accent-color: var(--mall-brand); }
.address-card__main { flex: 1; min-width: 0; }
.address-card__who { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; font-size: 14px; }
.address-card__who strong { font-weight: 700; }
.address-card__who span { color: var(--mall-ink-2); }
.address-card__badge {
  padding: 1px 6px; border-radius: 4px; background: var(--mall-brand); color: #fff !important; font-size: 11px;
}
.address-card__detail { margin-top: 5px; color: var(--mall-ink-2); font-size: 13px; line-height: 1.5; }
.address-card__edit { flex-shrink: 0; color: var(--mall-ink-3); font-size: 12px; }
.address-card__edit:hover { color: var(--mall-brand); }
.address-empty { color: var(--mall-ink-2); font-size: 13px; }
.address-empty__link { color: var(--mall-brand); text-decoration: underline; }
.item-list { display: flex; flex-direction: column; }
.item { display: flex; align-items: center; gap: 14px; padding: 12px 0; border-top: 1px solid var(--mall-line); }
.item:first-child { border-top: none; padding-top: 0; }
.item__media { width: 68px; height: 68px; border-radius: 8px; flex-shrink: 0; }
.item__info { flex: 1; min-width: 0; }
.item__name { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item__spec { margin-top: 4px; color: var(--mall-ink-3); font-size: 12px; }
.item__price { display: flex; flex-direction: column; align-items: flex-end; gap: 3px; font-size: 13px; }
.item__price strong { font-weight: 700; }
.item__price span { color: var(--mall-ink-3); font-size: 12px; }
.remark {
  width: 100%; padding: 10px 12px; border: 1px solid var(--mall-line); border-radius: 10px;
  font: inherit; font-size: 13px; resize: vertical;
}
.remark:focus { outline: none; border-color: var(--mall-brand); }
.summary {
  position: sticky; top: calc(var(--mall-header-h) + 16px); padding: 20px;
  border-radius: var(--mall-radius); background: #fff;
}
.summary h2 { font-size: 16px; font-weight: 800; }
.summary__list { display: flex; flex-direction: column; gap: 10px; margin: 16px 0; font-size: 13px; }
.summary__list > div { display: flex; align-items: center; justify-content: space-between; color: var(--mall-ink-2); }
.summary__free { color: var(--mall-success); }
.summary__total { padding-top: 12px; border-top: 1px solid var(--mall-line); color: var(--mall-ink) !important; }
.summary__total dd { color: var(--mall-brand); font-size: 22px; font-weight: 800; }
.summary__hint { margin-bottom: 10px; color: #b7791f; font-size: 12px; }
.summary__submit { width: 100%; }
.summary__note { display: flex; align-items: center; justify-content: center; gap: 5px; margin-top: 10px; color: var(--mall-ink-3); font-size: 11px; }
.checkout-skeleton { display: flex; flex-direction: column; gap: 16px; }
.skeleton-block { height: 160px; border-radius: var(--mall-radius); background: linear-gradient(90deg, #f2f2f2, #fafafa, #f2f2f2); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
.checkout-error { padding: 20px; border-radius: var(--mall-radius); background: #fff; }
@media (max-width: 960px) {
  .checkout-layout { grid-template-columns: minmax(0, 1fr); }
  .summary { position: static; }
}
@media (max-width: 760px) {
  .checkout-title { font-size: 20px; }
  .panel { padding: 14px; }
  .item__media { width: 56px; height: 56px; }
}
</style>
