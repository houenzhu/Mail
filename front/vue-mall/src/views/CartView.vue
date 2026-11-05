<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppToast from '@/components/common/AppToast.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import FloatingToolbar from '@/components/layout/FloatingToolbar.vue'
import CartItemRow from '@/components/cart/CartItemRow.vue'
import CartSummaryBar from '@/components/cart/CartSummaryBar.vue'
import { useCartStore } from '@/stores/cart'
import { messageForError } from '@/utils/errors'

const router = useRouter()
const cartStore = useCartStore()
const toastRef = ref(null)
const busySkuId = ref(null)

const items = computed(() => cartStore.items)
const hasItems = computed(() => cartStore.hasItems)
const validItems = computed(() => cartStore.validItems)
const invalidItems = computed(() => cartStore.invalidItems)
const isInitialLoading = computed(() => cartStore.loading && !cartStore.loaded && !items.value.length)
const loadError = computed(() => cartStore.error)

async function load(force = false) {
  try {
    await cartStore.load({ force })
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '购物车加载失败'), 'error')
  }
}

async function toggleItem(item, selected) {
  busySkuId.value = item.skuId
  try {
    await cartStore.toggleItem(item.skuId, selected)
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '勾选状态更新失败'), 'error')
  } finally {
    busySkuId.value = null
  }
}

async function toggleAll(selected) {
  try {
    await cartStore.toggleAll(selected)
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '全选操作失败'), 'error')
  }
}

async function changeQuantity(item, quantity) {
  if (quantity === item.quantity) return
  busySkuId.value = item.skuId
  try {
    await cartStore.changeQuantity(item.skuId, quantity)
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '数量修改失败'), 'error')
    await load(true)
  } finally {
    busySkuId.value = null
  }
}

async function remove(item) {
  busySkuId.value = item.skuId
  try {
    await cartStore.remove(item.skuId)
    toastRef.value?.show('已从购物车移除')
  } catch (cause) {
    toastRef.value?.show(messageForError(cause, '删除失败，请稍后重试'), 'error')
  } finally {
    busySkuId.value = null
  }
}

async function openCheckout() {
  if (cartStore.selectedCount <= 0) {
    toastRef.value?.show('请先选择要结算的商品', 'error')
    return
  }
  // 价格与失效状态在结算页会重新拉一次，这里只做入口校验，避免带着旧金额进入下单
  await router.push({ name: 'checkout' })
}

onMounted(() => load(true))
</script>

<template>
  <div class="cart-page">
    <SiteHeader />

    <main class="mall-container cart-main">
      <nav class="cart-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>购物车</span>
      </nav>

      <header class="cart-head">
        <div>
          <h1>购物车</h1>
          <p v-if="hasItems">
            共 {{ cartStore.totalQuantity }} 件商品
            <span v-if="invalidItems.length" class="cart-head__invalid">
              · {{ invalidItems.length }} 件已失效
            </span>
          </p>
          <p v-else>挑好的好物，先放在这里</p>
        </div>
        <RouterLink class="cart-head__continue" :to="{ name: 'search', query: { q: '' } }">
          继续购物
          <AppIcon name="arrow-right" :size="13" />
        </RouterLink>
      </header>

      <section v-if="isInitialLoading" class="cart-loading" aria-busy="true" aria-label="购物车加载中">
        <div v-for="index in 4" :key="index" class="cart-loading__row">
          <span class="cart-loading__check skeleton" />
          <span class="cart-loading__media skeleton" />
          <span class="cart-loading__lines">
            <span class="skeleton" />
            <span class="skeleton" />
          </span>
          <span class="cart-loading__price skeleton" />
        </div>
      </section>

      <section v-else-if="loadError && !items.length" class="cart-error" role="alert">
        <EmptyState
          icon="cart"
          title="购物车加载失败"
          :description="loadError"
        >
          <BaseButton size="sm" @click="load(true)">重新加载</BaseButton>
        </EmptyState>
      </section>

      <template v-else-if="hasItems">
        <section class="cart-table" aria-label="购物车商品">
          <div class="cart-table__head" aria-hidden="true">
            <span>商品信息</span>
            <span>单价</span>
            <span>数量</span>
            <span>小计</span>
            <span>操作</span>
          </div>

          <CartItemRow
            v-for="item in items"
            :key="item.skuId"
            :item="item"
            :busy="cartStore.selecting && busySkuId === item.skuId"
            :removing="cartStore.removingSkuId === item.skuId"
            @toggle="toggleItem(item, $event)"
            @quantity="changeQuantity(item, $event)"
            @remove="remove(item)"
          />
        </section>

        <p v-if="invalidItems.length" class="cart-tip">
          <AppIcon name="close" :size="13" />
          失效商品不参与结算，可单独删除后继续购物。
        </p>

        <CartSummaryBar
          :all-selected="cartStore.allSelected"
          :selected-count="cartStore.selectedCount"
          :selected-amount="cartStore.selectedAmount"
          :valid-count="validItems.length"
          :selecting="cartStore.selecting"
          @toggle-all="toggleAll"
          @checkout="openCheckout"
        />
      </template>

      <section v-else class="cart-empty">
        <EmptyState
          icon="cart"
          title="购物车还是空的"
          description="去首页逛逛，把喜欢的好物先放进来。"
        >
          <BaseButton :to="{ name: 'home' }">去逛逛</BaseButton>
        </EmptyState>

        <div class="cart-empty__tips">
          <p>
            <AppIcon name="shield-check" :size="14" />
            正品保障
          </p>
          <p>
            <AppIcon name="truck" :size="14" />
            满 99 包邮
          </p>
          <p>
            <AppIcon name="refresh" :size="14" />
            7 天无理由
          </p>
        </div>
      </section>
    </main>

    <SiteFooter />
    <FloatingToolbar />
    <AppToast ref="toastRef" />
  </div>
</template>

<style scoped>
.cart-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.cart-main {
  flex: 1;
  padding-top: 18px;
  padding-bottom: 28px;
}

.cart-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.cart-breadcrumb a:hover {
  color: var(--mall-brand);
}

.cart-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin: 14px 0 16px;
}

.cart-head h1 {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.cart-head p {
  margin-top: 5px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.cart-head__invalid {
  color: #b7791f;
}

.cart-head__continue {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.cart-head__continue:hover {
  color: var(--mall-brand);
}

.cart-table {
  overflow: hidden;
  border: 1px solid var(--mall-line);
  border-radius: var(--mall-radius);
  background: #fff;
}

.cart-table__head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px 132px 108px 62px;
  gap: 16px;
  padding: 12px 16px 12px 138px;
  border-bottom: 1px solid var(--mall-line);
  background: var(--mall-surface-2);
  color: var(--mall-ink-3);
  font-size: 12px;
}

.cart-tip {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  color: #b7791f;
  font-size: 12px;
}

.cart-loading {
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: #fff;
}

.cart-loading__row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 16px;
  border-bottom: 1px solid var(--mall-line);
}

.cart-loading__check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cart-loading__media {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  flex-shrink: 0;
}

.cart-loading__lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.cart-loading__lines span {
  display: block;
  height: 13px;
  border-radius: 4px;
}

.cart-loading__lines span:last-child {
  width: 42%;
}

.cart-loading__price {
  width: 90px;
  height: 20px;
  border-radius: 4px;
}

.cart-empty {
  padding: 20px 16px 26px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
}

.cart-empty__tips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 20px;
  padding-top: 6px;
  border-top: 1px solid var(--mall-line);
  color: var(--mall-ink-3);
  font-size: 12px;
}

.cart-empty__tips p {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cart-empty__tips :deep(svg) {
  color: var(--mall-brand);
}

.cart-error {
  border-radius: var(--mall-radius-lg);
  background: #fff;
}

@media (max-width: 960px) {
  .cart-table__head {
    display: none;
  }
}

@media (max-width: 760px) {
  .cart-main {
    padding-bottom: 92px;
  }

  .cart-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
    margin-block: 12px;
  }

  .cart-head h1 {
    font-size: 20px;
  }

  .cart-head__continue {
    display: none;
  }

  .cart-loading__media {
    width: 72px;
    height: 72px;
  }

  .cart-empty {
    padding: 8px 4px 20px;
  }

  .cart-empty__tips {
    gap: 12px;
  }
}
</style>