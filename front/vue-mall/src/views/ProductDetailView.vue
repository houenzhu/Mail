<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseTag from '@/components/common/BaseTag.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import AppToast from '@/components/common/AppToast.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import FloatingToolbar from '@/components/layout/FloatingToolbar.vue'
import ProductGrid from '@/components/product/ProductGrid.vue'
import QuantityStepper from '@/components/product/QuantityStepper.vue'
import SkuSelector from '@/components/product/SkuSelector.vue'
import { fetchProductDetail, fetchProductPage } from '@/api/product'
import { messageForError } from '@/utils/errors'
import { formatPrice } from '@/utils/format'
import { formatSpec } from '@/utils/spec'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const loading = ref(true)
const detail = ref(null)
const error = ref('')
const errorCode = ref(null)
const selectedSku = ref(null)
const quantity = ref(1)
const adding = ref(false)
const activeTab = ref('detail')
const recommends = ref([])
const recommendLoading = ref(false)
const toastRef = ref(null)

const product = computed(() => detail.value?.product || null)
const skus = computed(() => detail.value?.skus || [])
const currentSku = computed(() => selectedSku.value || null)
const maxQuantity = computed(() => {
  if (!currentSku.value) return 1
  if (currentSku.value.available === null) return 99
  return Math.max(1, Math.min(99, currentSku.value.available))
})
const currentPrice = computed(() => currentSku.value?.price || detail.value?.lowPrice || 0)
const priceRange = computed(() => {
  if (!detail.value) return ''
  if (detail.value.lowPrice === detail.value.highPrice) return `¥${formatPrice(detail.value.lowPrice)}`
  return `¥${formatPrice(detail.value.lowPrice)} - ${formatPrice(detail.value.highPrice)}`
})
const currentSpecText = computed(() => {
  if (!currentSku.value) return ''
  return formatSpec(currentSku.value.specJson, currentSku.value.name)
})
const stockText = computed(() => {
  if (!currentSku.value) return ''
  if (currentSku.value.available === null) return '库存待确认'
  if (currentSku.value.available <= 0) return '暂时售罄'
  if (currentSku.value.available <= 5) return `仅剩 ${currentSku.value.available} 件`
  return `现货 ${currentSku.value.available} 件`
})
const canAdd = computed(() => Boolean(
  currentSku.value
  && currentSku.value.status === 1
  && (currentSku.value.available === null || currentSku.value.available > 0),
))
const isOffShelf = computed(() => errorCode.value === 32002 || product.value?.status === 0)
const galleryImage = computed(() => (
  selectedSku.value?.image || product.value?.image || ''
))
const galleryKey = computed(() => product.value?.imageKey || 'default')

const tabs = [
  { value: 'detail', label: '商品详情' },
  { value: 'spec', label: '规格参数' },
  { value: 'service', label: '售后保障' },
]

const marketingPoints = [
  { icon: 'shield-check', title: '正品保障', desc: '品牌直供' },
  { icon: 'truck', title: '满 99 包邮', desc: '48 小时内发货' },
  { icon: 'refresh', title: '7 天无理由', desc: '退换更省心' },
]

watch(
  () => currentSku.value?.id,
  () => {
    quantity.value = 1
  },
)

function handleError(cause) {
  error.value = messageForError(cause, '商品详情加载失败，请稍后重试')
  errorCode.value = cause?.code ?? null
  detail.value = null
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  errorCode.value = null
  selectedSku.value = null
  try {
    detail.value = await fetchProductDetail(route.params.id)
    if (!detail.value.skus.length) {
      error.value = '该商品暂未配置可售规格'
    }
  } catch (cause) {
    handleError(cause)
  } finally {
    loading.value = false
  }
}

async function loadRecommends() {
  recommendLoading.value = true
  try {
    const page = await fetchProductPage({ pageNo: 1, pageSize: 8 })
    recommends.value = page.list.filter((item) => String(item.id) !== String(route.params.id)).slice(0, 4)
  } catch {
    recommends.value = []
  } finally {
    recommendLoading.value = false
  }
}

async function addToCart() {
  if (!canAdd.value || adding.value) return
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath, reason: 'cart' } })
    return
  }
  adding.value = true
  try {
    await cartStore.add(currentSku.value.id, quantity.value)
    toastRef.value?.show('已加入购物车')
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '加入购物车失败'), 'error')
  } finally {
    adding.value = false
  }
}

function goCart() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/cart' } })
    return
  }
  router.push({ name: 'cart' })
}

watch(
  () => route.params.id,
  () => {
    loadDetail()
    loadRecommends()
  },
  { immediate: true },
)
</script>

<template>
  <div class="detail-page">
    <SiteHeader />

    <main class="mall-container detail-main">
      <nav class="detail-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <RouterLink :to="{ name: 'search', query: { q: '' } }">全部商品</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>{{ product?.title || '商品详情' }}</span>
      </nav>

      <div v-if="loading" class="detail-skeleton" aria-busy="true" aria-label="商品详情加载中">
        <div class="detail-skeleton__gallery skeleton" />
        <div class="detail-skeleton__info">
          <div class="detail-skeleton__line detail-skeleton__line--title skeleton" />
          <div class="detail-skeleton__line detail-skeleton__line--sub skeleton" />
          <div class="detail-skeleton__price skeleton" />
          <div class="detail-skeleton__line skeleton" />
          <div class="detail-skeleton__line detail-skeleton__line--short skeleton" />
          <div class="detail-skeleton__action skeleton" />
        </div>
      </div>

      <section v-else-if="error" class="detail-error" role="alert">
        <span class="detail-error__icon">
          <AppIcon :name="isOffShelf ? 'close' : 'search'" :size="26" />
        </span>
        <h1>{{ isOffShelf ? '商品已下架' : '商品加载失败' }}</h1>
        <p>{{ error }}</p>
        <div class="detail-error__actions">
          <BaseButton v-if="!isOffShelf" @click="loadDetail">重新加载</BaseButton>
          <BaseButton :variant="isOffShelf ? 'primary' : 'ghost'" :to="{ name: 'search', query: { q: '' } }">
            看看其他商品
          </BaseButton>
        </div>
      </section>

      <template v-else-if="product">
        <section class="detail-hero" aria-label="商品信息">
          <div class="detail-gallery">
            <SmartImage
              :key="galleryImage"
              class="detail-gallery__image"
              :src="galleryImage"
              :alt="product.title"
              :fallback-key="galleryKey"
              ratio="1"
              eager
              tone="#f6f2ed"
            />
            <div class="detail-gallery__badges">
              <BaseTag tone="brand" solid>品质好物</BaseTag>
              <BaseTag v-if="product.sales >= 500" tone="dark" solid>热卖</BaseTag>
            </div>
          </div>

          <div class="detail-info">
            <div class="detail-info__head">
              <h1 class="detail-info__title">{{ product.title }}</h1>
              <p v-if="product.subtitle" class="detail-info__subtitle">{{ product.subtitle }}</p>
            </div>

            <div class="detail-price-panel">
              <div class="detail-price-panel__main">
                <span class="detail-price-panel__label">价格</span>
                <strong class="detail-price-panel__price">¥{{ formatPrice(currentPrice) }}</strong>
                <del v-if="currentSku?.marketPrice > currentSku?.price" class="detail-price-panel__market">
                  ¥{{ formatPrice(currentSku.marketPrice) }}
                </del>
                <span v-if="priceRange.includes(' - ')" class="detail-price-panel__range">
                  规格价 {{ priceRange }}
                </span>
              </div>
              <div class="detail-price-panel__meta">
                <span>销量 {{ product.salesText }}</span>
                <span aria-hidden="true">·</span>
                <span>{{ stockText || '请选择规格' }}</span>
              </div>
            </div>

            <div class="detail-delivery">
              <span class="detail-delivery__label">配送</span>
              <div class="detail-delivery__body">
                <p>广东深圳发货 · 满 99 元包邮</p>
                <p class="detail-delivery__muted">48 小时内发出，支持 7 天无理由退换</p>
              </div>
            </div>

            <div class="detail-selector">
              <SkuSelector v-model="selectedSku" :skus="skus" />
            </div>

            <div class="detail-quantity">
              <span class="detail-quantity__label">数量</span>
              <QuantityStepper v-model="quantity" :max="maxQuantity" :disabled="!canAdd" />
              <span class="detail-quantity__hint">
                {{ currentSku?.available === null ? '库存以下单时为准' : `最多可购 ${maxQuantity} 件` }}
              </span>
            </div>

            <div class="detail-actions">
              <BaseButton
                size="lg"
                :disabled="!canAdd"
                :loading="adding"
                class="detail-actions__buy"
                @click="addToCart"
              >
                <AppIcon name="cart" :size="19" />
                {{ canAdd ? '加入购物车' : '暂时不可购买' }}
              </BaseButton>
              <BaseButton variant="ghost" size="lg" @click="goCart">
                去购物车
              </BaseButton>
            </div>

            <ul class="detail-promises" aria-label="服务承诺">
              <li v-for="item in marketingPoints" :key="item.title">
                <AppIcon :name="item.icon" :size="16" />
                <span>
                  <strong>{{ item.title }}</strong>
                  {{ item.desc }}
                </span>
              </li>
            </ul>
          </div>
        </section>

        <section class="detail-panel" aria-label="商品介绍">
          <div class="detail-tabs" role="tablist" aria-label="商品信息分类">
            <button
              v-for="tab in tabs"
              :key="tab.value"
              type="button"
              role="tab"
              class="detail-tabs__item"
              :class="{ 'is-active': activeTab === tab.value }"
              :aria-selected="activeTab === tab.value"
              @click="activeTab = tab.value"
            >
              {{ tab.label }}
            </button>
          </div>

          <div v-if="activeTab === 'detail'" class="detail-copy" role="tabpanel">
            <p>
              {{ product.title }}，为日常使用场景挑选的实用好物。{{ product.subtitle || '细节经过反复打磨，兼顾手感与耐用度。' }}
            </p>
            <p>
              每件商品出库前均经过外观与功能检查；实际颜色可能因屏幕显示略有差异，以收到实物为准。
            </p>
            <div class="detail-copy__grid">
              <div>
                <AppIcon name="shield-check" :size="18" />
                <strong>正品直供</strong>
                <span>品牌授权渠道，支持验真</span>
              </div>
              <div>
                <AppIcon name="truck" :size="18" />
                <strong>快速发货</strong>
                <span>付款后 48 小时内出库</span>
              </div>
              <div>
                <AppIcon name="refresh" :size="18" />
                <strong>无忧退换</strong>
                <span>7 天无理由，运费险可选</span>
              </div>
            </div>
          </div>

          <div v-else-if="activeTab === 'spec'" class="detail-spec" role="tabpanel">
            <dl>
              <div>
                <dt>商品编号</dt>
                <dd>{{ product.id }}</dd>
              </div>
              <div>
                <dt>商品名称</dt>
                <dd>{{ product.title }}</dd>
              </div>
              <div>
                <dt>所属分类</dt>
                <dd>{{ product.categoryId ? `分类 ${product.categoryId}` : '—' }}</dd>
              </div>
              <div>
                <dt>当前规格</dt>
                <dd>{{ currentSpecText || '请选择规格' }}</dd>
              </div>
              <div>
                <dt>可售库存</dt>
                <dd>{{ stockText || '请选择规格' }}</dd>
              </div>
            </dl>
          </div>

          <div v-else class="detail-copy" role="tabpanel">
            <p>支持 7 天无理由退换；商品需保持完好、不影响二次销售，并保留原包装与配件。</p>
            <p>如收到商品存在破损、缺件或功能异常，请在签收后 48 小时内联系客服，我们会优先为你处理。</p>
          </div>
        </section>

        <section v-if="recommends.length" class="detail-recommend" aria-labelledby="detail-recommend-title">
          <div class="detail-recommend__head">
            <h2 id="detail-recommend-title">看了又看</h2>
            <RouterLink :to="{ name: 'search', query: { q: '' } }">
              更多好物
              <AppIcon name="arrow-right" :size="13" />
            </RouterLink>
          </div>
          <ProductGrid
            :products="recommends"
            :loading="recommendLoading"
            :skeleton-count="4"
            :columns="4"
            empty-title="暂无更多推荐"
            empty-description="去首页看看其他精选好物吧。"
          />
        </section>
      </template>
    </main>

    <SiteFooter />
    <FloatingToolbar />
    <AppToast ref="toastRef" />
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.detail-main {
  flex: 1;
  padding-top: 18px;
}

.detail-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-breadcrumb a:hover {
  color: var(--mall-brand);
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(0, 460px) minmax(0, 1fr);
  gap: 36px;
  margin-top: 14px;
  padding: 24px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  box-shadow: var(--mall-shadow-sm);
}

.detail-gallery {
  position: relative;
  align-self: start;
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: var(--mall-surface-2);
}

.detail-gallery__badges {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  gap: 6px;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
  padding-top: 4px;
}

.detail-info__title {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.01em;
}

.detail-info__subtitle {
  margin-top: 8px;
  color: var(--mall-ink-2);
  font-size: 14px;
  line-height: 1.7;
}

.detail-price-panel {
  padding: 16px 18px;
  border-radius: var(--mall-radius);
  background: linear-gradient(120deg, #fff4ee, #fff9f6);
}

.detail-price-panel__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.detail-price-panel__label {
  align-self: center;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-price-panel__price {
  color: var(--mall-brand);
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.detail-price-panel__market {
  color: var(--mall-ink-4);
  font-size: 13px;
}

.detail-price-panel__range {
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-price-panel__meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 8px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-delivery {
  display: flex;
  gap: 14px;
  font-size: 13px;
}

.detail-delivery__label,
.detail-quantity__label {
  flex-shrink: 0;
  width: 32px;
  color: var(--mall-ink-3);
}

.detail-delivery__body p {
  line-height: 1.6;
}

.detail-delivery__muted {
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-selector {
  padding-top: 16px;
  border-top: 1px solid var(--mall-line);
}

.detail-quantity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-quantity__hint {
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 4px;
}

.detail-actions__buy {
  min-width: 210px;
}

.detail-promises {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 2px;
  padding-top: 16px;
  border-top: 1px solid var(--mall-line);
}

.detail-promises li {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: var(--mall-brand);
}

.detail-promises span {
  display: flex;
  flex-direction: column;
  gap: 1px;
  color: var(--mall-ink-3);
  font-size: 11px;
}

.detail-promises strong {
  color: var(--mall-ink-2);
  font-size: 12px;
  font-weight: 600;
}

.detail-panel {
  margin-top: 16px;
  padding: 0 24px 24px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  box-shadow: var(--mall-shadow-sm);
}

.detail-tabs {
  display: flex;
  gap: 26px;
  border-bottom: 1px solid var(--mall-line);
}

.detail-tabs__item {
  position: relative;
  height: 52px;
  color: var(--mall-ink-2);
  font-size: 14px;
  font-weight: 500;
}

.detail-tabs__item::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: transparent;
}

.detail-tabs__item.is-active {
  color: var(--mall-brand);
  font-weight: 600;
}

.detail-tabs__item.is-active::after {
  background: var(--mall-brand);
}

.detail-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 20px;
  color: var(--mall-ink-2);
  font-size: 13px;
  line-height: 1.9;
}

.detail-copy__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.detail-copy__grid > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px;
  border-radius: 10px;
  background: var(--mall-surface-2);
  color: var(--mall-brand);
}

.detail-copy__grid strong {
  color: var(--mall-ink);
  font-size: 13px;
}

.detail-copy__grid span {
  color: var(--mall-ink-3);
  font-size: 12px;
  line-height: 1.6;
}

.detail-spec dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 28px;
  padding-top: 8px;
}

.detail-spec dl > div {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--mall-line);
  font-size: 13px;
}

.detail-spec dt {
  width: 76px;
  flex-shrink: 0;
  color: var(--mall-ink-3);
}

.detail-spec dd {
  color: var(--mall-ink);
}

.detail-recommend {
  margin-top: 32px;
}

.detail-recommend__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.detail-recommend__head h2 {
  font-size: 20px;
  font-weight: 800;
}

.detail-recommend__head a {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.detail-recommend__head a:hover {
  color: var(--mall-brand);
}

.detail-skeleton {
  display: grid;
  grid-template-columns: minmax(0, 460px) minmax(0, 1fr);
  gap: 36px;
  margin-top: 22px;
  padding: 24px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
}

.detail-skeleton__gallery {
  aspect-ratio: 1;
  border-radius: var(--mall-radius);
}

.detail-skeleton__info {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 8px;
}

.detail-skeleton__line {
  height: 16px;
  border-radius: 5px;
}

.detail-skeleton__line--title {
  height: 30px;
  width: 82%;
}

.detail-skeleton__line--sub {
  width: 56%;
}

.detail-skeleton__line--short {
  width: 42%;
}

.detail-skeleton__price {
  height: 76px;
  border-radius: var(--mall-radius);
}

.detail-skeleton__action {
  width: 56%;
  height: 48px;
  margin-top: 10px;
  border-radius: 999px;
}

.detail-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 22px;
  padding: 64px 24px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  text-align: center;
}

.detail-error__icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 4px;
  border-radius: 50%;
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.detail-error h1 {
  font-size: 20px;
  font-weight: 700;
}

.detail-error p {
  max-width: 420px;
  color: var(--mall-ink-3);
  font-size: 13px;
}

.detail-error__actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

@media (max-width: 960px) {
  .detail-hero,
  .detail-skeleton {
    grid-template-columns: minmax(0, 360px) minmax(0, 1fr);
    gap: 24px;
  }

  .detail-info__title {
    font-size: 22px;
  }

  .detail-price-panel__price {
    font-size: 28px;
  }
}

@media (max-width: 760px) {
  .detail-hero,
  .detail-skeleton {
    grid-template-columns: 1fr;
    gap: 18px;
    padding: 14px;
  }

  .detail-hero {
    margin-top: 10px;
  }

  .detail-gallery {
    max-width: 480px;
    margin-inline: auto;
  }

  .detail-info__title {
    font-size: 20px;
  }

  .detail-price-panel__price {
    font-size: 26px;
  }

  .detail-promises {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .detail-promises li {
    align-items: center;
  }

  .detail-promises span {
    flex-direction: row;
    gap: 6px;
  }

  .detail-panel {
    padding: 0 14px 18px;
  }

  .detail-tabs {
    gap: 18px;
  }

  .detail-tabs__item {
    height: 46px;
    font-size: 13px;
  }

  .detail-copy__grid {
    grid-template-columns: 1fr;
  }

  .detail-spec dl {
    grid-template-columns: 1fr;
  }

  .detail-actions__buy {
    flex: 1;
    min-width: 0;
  }

  .detail-skeleton__gallery {
    max-width: 480px;
    margin-inline: auto;
  }

  .detail-error {
    padding: 48px 18px;
  }
}
</style>