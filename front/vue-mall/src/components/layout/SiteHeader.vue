<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import SearchBar from './SearchBar.vue'
import { headerShortcuts } from '@/data/homeContent'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

defineProps({
  compact: { type: Boolean, default: false },
})

const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()
const open = ref(false)
const accountMenuOpen = ref(false)
const accountMenuRef = ref(null)
const accountTriggerRef = ref(null)

const isLoggedIn = computed(() => userStore.isLoggedIn)
const displayName = computed(() => userStore.displayName)
const cartBadge = computed(() => {
  const count = cartStore.totalQuantity
  return count > 99 ? '99+' : String(count)
})
const cartTarget = computed(() => (
  isLoggedIn.value
    ? { name: 'cart' }
    : { name: 'login', query: { redirect: '/cart' } }
))

const shortcuts = computed(() =>
  headerShortcuts.map((item) => {
    if (item.feature === 'orders' || item.feature === 'favorites') {
      return { ...item, requiresAuth: true }
    }
    return item
  }),
)

function targetFor(item) {
  if (item.feature === 'cart') return cartTarget.value
  if (item.feature === 'orders') {
    return isLoggedIn.value
      ? { name: 'order-list' }
      : { name: 'login', query: { redirect: '/order/list', reason: 'required' } }
  }
  if (item.feature === 'favorites' && isLoggedIn.value) {
    return { name: 'account' }
  }
  return { name: 'feature', params: { feature: item.feature } }
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

function onDocumentPointerDown(event) {
  if (!accountMenuOpen.value) return
  const target = event.target
  if (accountMenuRef.value?.contains(target) || accountTriggerRef.value?.contains(target)) return
  closeAccountMenu()
}

function onDocumentKeydown(event) {
  if (event.key !== 'Escape') return
  if (accountMenuOpen.value) {
    closeAccountMenu()
    accountTriggerRef.value?.focus()
  }
  open.value = false
}

watch(
  () => route.fullPath,
  () => {
    open.value = false
    accountMenuOpen.value = false
  },
)

watch(
  () => [isLoggedIn.value, cartStore.loaded],
  ([loggedIn, loaded]) => {
    if (loggedIn && !loaded) cartStore.load().catch(() => {})
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <header class="site-header" :class="{ 'is-compact': compact }">
    <div class="site-header__utility">
      <div class="mall-container site-header__utility-inner">
        <div class="site-header__utility-left">
          <template v-if="isLoggedIn">
            <span class="site-header__hello">你好，{{ displayName }}</span>
            <RouterLink class="site-header__login" :to="{ name: 'account' }">
              账户中心
            </RouterLink>
            <span class="site-header__divider" aria-hidden="true" />
          </template>
          <template v-else>
            <span class="site-header__hello">亲，欢迎来到好物集</span>
            <RouterLink
              class="site-header__login"
              :to="{ name: 'login', query: { redirect: route.fullPath } }"
            >
              请登录
            </RouterLink>
            <RouterLink
              class="site-header__register"
              :to="{ name: 'register', query: { redirect: route.fullPath } }"
            >
              免费注册
            </RouterLink>
            <span class="site-header__divider" aria-hidden="true" />
          </template>
          <RouterLink
            class="site-header__utility-link"
            :to="{ name: 'feature', params: { feature: 'accessibility' } }"
          >
            无障碍
          </RouterLink>
        </div>

        <div class="site-header__utility-right">
          <RouterLink class="site-header__utility-link" :to="{ name: 'home' }">
            商城首页
          </RouterLink>
          <RouterLink
            v-for="item in shortcuts"
            :key="item.feature"
            class="site-header__utility-link"
            :to="targetFor(item)"
          >
            {{ item.label }}
          </RouterLink>
        </div>
      </div>
    </div>

    <div class="site-header__main">
      <div class="mall-container site-header__main-inner">
        <RouterLink class="brand" :to="{ name: 'home' }" aria-label="好物集商城首页">
          <span class="brand__mark" aria-hidden="true">
            <span class="brand__mark-dot" />
            <span class="brand__mark-arc" />
          </span>
          <span class="brand__text">
            <strong>好物集</strong>
            <small>GOODMALL</small>
          </span>
        </RouterLink>

        <div class="site-header__search">
          <SearchBar initial-keyword="" />
        </div>

        <div class="site-header__actions">
          <div v-if="isLoggedIn" ref="accountMenuRef" class="header-account">
            <button
              ref="accountTriggerRef"
              class="header-account__trigger"
              type="button"
              :aria-expanded="accountMenuOpen"
              aria-haspopup="menu"
              @click="accountMenuOpen = !accountMenuOpen"
            >
              <span class="header-account__avatar" aria-hidden="true">
                {{ displayName.slice(0, 1) }}
              </span>
              <span class="header-account__name">{{ displayName }}</span>
              <AppIcon name="chevron-down" :size="12" />
            </button>

            <div v-if="accountMenuOpen" class="header-account__menu" role="menu">
              <RouterLink role="menuitem" :to="{ name: 'account' }">账户中心</RouterLink>
              <RouterLink
                role="menuitem"
                :to="{ name: 'order-list' }"
              >
                我的订单
              </RouterLink>
              <RouterLink
                role="menuitem"
                :to="{ name: 'feature', params: { feature: 'favorites' } }"
              >
                我的收藏
              </RouterLink>
            </div>
          </div>

          <RouterLink
            v-else
            class="header-action header-action--login"
            :to="{ name: 'login', query: { redirect: route.fullPath } }"
          >
            <AppIcon name="user" :size="18" />
            <span>登录</span>
          </RouterLink>

          <RouterLink
            class="header-action"
            :to="isLoggedIn
              ? { name: 'account' }
              : { name: 'login', query: { redirect: route.fullPath } }"
          >
            <AppIcon name="heart" :size="20" />
            <span>收藏</span>
          </RouterLink>
          <RouterLink
            class="header-action header-action--cart"
            :to="cartTarget"
          >
            <AppIcon name="cart" :size="20" />
            <span>购物车</span>
            <em v-if="cartStore.totalQuantity" class="header-action__badge">{{ cartBadge }}</em>
          </RouterLink>
          <button
            class="header-menu-toggle"
            type="button"
            :aria-expanded="open"
            aria-label="展开更多入口"
            @click="open = !open"
          >
            <AppIcon :name="open ? 'close' : 'menu'" :size="20" />
          </button>
        </div>
      </div>

      <div v-if="open" class="site-header__mobile-menu">
        <div class="mall-container site-header__mobile-grid">
          <RouterLink
            v-if="!isLoggedIn"
            class="site-header__mobile-link site-header__mobile-link--primary"
            :to="{ name: 'login', query: { redirect: route.fullPath } }"
            @click="open = false"
          >
            登录 / 注册
          </RouterLink>
          <RouterLink
            v-else
            class="site-header__mobile-link site-header__mobile-link--primary"
            :to="{ name: 'account' }"
            @click="open = false"
          >
            账户中心
          </RouterLink>
          <RouterLink
            v-for="item in shortcuts"
            :key="item.feature"
            class="site-header__mobile-link"
            :to="targetFor(item)"
            @click="open = false"
          >
            {{ item.label }}
          </RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>
<style scoped>
.site-header {
  position: relative;
  z-index: 40;
  background: #fff;
}

.site-header__utility {
  border-bottom: 1px solid var(--mall-line);
  background: #f8f8f8;
  color: var(--mall-ink-2);
  font-size: 12px;
}

.site-header__utility-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  gap: 16px;
}

.site-header__utility-left,
.site-header__utility-right {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.site-header__hello {
  color: var(--mall-ink-3);
}

.site-header__login {
  color: var(--mall-brand);
  font-weight: 600;
}

.site-header__register {
  color: var(--mall-ink-2);
}

.site-header__register:hover,
.site-header__utility-link:hover {
  color: var(--mall-brand);
}

.site-header__divider {
  width: 1px;
  height: 11px;
  background: var(--mall-line-strong);
}

.site-header__utility-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--mall-ink-2);
  white-space: nowrap;
  transition: color 0.15s var(--mall-ease);
}

.site-header__main {
  background: linear-gradient(180deg, #fff 0%, #fff 62%, rgba(255, 240, 232, 0.55) 100%);
}

.site-header__main-inner {
  display: grid;
  grid-template-columns: 236px minmax(0, 620px) 1fr;
  align-items: center;
  gap: 28px;
  min-height: var(--mall-header-h);
  padding-block: 14px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.brand__mark {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--mall-brand);
  color: #fff;
  overflow: hidden;
}

.brand__mark-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  top: 12px;
  left: 12px;
}

.brand__mark-arc {
  position: absolute;
  width: 24px;
  height: 24px;
  border: 2px solid rgba(255, 255, 255, 0.85);
  border-radius: 50%;
  right: 5px;
  bottom: -6px;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand__text strong {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.brand__text small {
  margin-top: 4px;
  color: var(--mall-ink-3);
  font-size: 10px;
  letter-spacing: 0.34em;
}

.site-header__search {
  width: 100%;
}

.site-header__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.header-account {
  position: relative;
}

.header-account__trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 12px 0 4px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--mall-ink-2);
  font-size: 13px;
  white-space: nowrap;
}

.header-account__trigger:hover {
  border-color: var(--mall-brand);
  color: var(--mall-brand);
}

.header-account__avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(140deg, #ff8a4c, #ff5000);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.header-account__name {
  max-width: 76px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.header-account__menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  min-width: 148px;
  padding: 6px;
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--mall-shadow);
}

.header-account__menu a {
  padding: 9px 12px;
  border-radius: 8px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.header-account__menu a:hover {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.header-action {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 999px;
  background: #fff;
  font-size: 13px;
  color: var(--mall-ink-2);
  white-space: nowrap;
  transition:
    border-color 0.2s var(--mall-ease),
    color 0.2s var(--mall-ease),
    box-shadow 0.2s var(--mall-ease);
}

.header-action:hover {
  border-color: var(--mall-brand);
  color: var(--mall-brand);
  box-shadow: var(--mall-shadow-sm);
}

.header-action--login {
  border-color: var(--mall-brand);
  color: var(--mall-brand);
}

.header-action--cart {
  border-color: var(--mall-brand);
  background: var(--mall-brand);
  color: #fff;
}

.header-action--cart:hover {
  background: var(--mall-brand-deep);
  color: #fff;
}

.header-action__badge {
  position: absolute;
  top: -6px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--mall-brand);
  color: var(--mall-brand);
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}

.header-menu-toggle {
  display: none;
  width: 38px;
  height: 38px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 50%;
  background: #fff;
  align-items: center;
  justify-content: center;
}

.site-header__mobile-menu {
  border-top: 1px solid var(--mall-line);
  background: #fff;
  animation: header-menu-in 0.18s var(--mall-ease);
}

.site-header__mobile-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding-block: 12px;
}

.site-header__mobile-link {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--mall-surface-2);
  font-size: 13px;
  text-align: center;
}

.site-header__mobile-link--primary {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
  font-weight: 600;
}

@keyframes header-menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

@media (max-width: 1080px) {
  .site-header__main-inner {
    grid-template-columns: 200px minmax(0, 1fr) auto;
    gap: 18px;
  }

  .header-action {
    padding: 0 10px;
  }
}

@media (max-width: 860px) {
  .site-header__utility-right {
    display: none;
  }

  .site-header__main-inner {
    grid-template-columns: 1fr auto;
    grid-template-areas:
      'brand actions'
      'search search';
    row-gap: 12px;
  }

  .brand {
    grid-area: brand;
  }

  .site-header__search {
    grid-area: search;
  }

  .site-header__actions {
    grid-area: actions;
  }

  .header-action {
    display: none;
  }

  .header-account {
    display: none;
  }

  .header-menu-toggle {
    display: inline-flex;
  }
}

@media (max-width: 560px) {
  .site-header__utility-inner {
    min-height: 30px;
  }

  .site-header__hello {
    display: none;
  }

  .site-header__utility-left {
    gap: 10px;
  }

  .brand__mark {
    width: 38px;
    height: 38px;
    border-radius: 12px;
  }

  .brand__text strong {
    font-size: 21px;
  }

  .site-header__main-inner {
    padding-block: 10px;
  }
}
</style>
