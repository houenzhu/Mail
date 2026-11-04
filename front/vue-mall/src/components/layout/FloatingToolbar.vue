<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'

const visible = ref(false)

function onScroll() {
  visible.value = window.scrollY > 480
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <aside class="floating-toolbar" aria-label="快捷工具">
    <RouterLink
      class="floating-toolbar__item"
      :to="{ name: 'feature', params: { feature: 'help' } }"
      aria-label="在线客服"
    >
      <AppIcon name="headset" :size="19" />
      <span>客服</span>
    </RouterLink>
    <RouterLink
      class="floating-toolbar__item"
      :to="{ name: 'feature', params: { feature: 'cart' } }"
      aria-label="购物车"
    >
      <AppIcon name="cart" :size="19" />
      <span>购物车</span>
    </RouterLink>
    <button
      class="floating-toolbar__item floating-toolbar__item--top"
      :class="{ 'is-visible': visible }"
      type="button"
      aria-label="回到顶部"
      :tabindex="visible ? 0 : -1"
      @click="toTop"
    >
      <AppIcon name="arrow-up" :size="19" />
      <span>顶部</span>
    </button>
  </aside>
</template>

<style scoped>
.floating-toolbar {
  position: fixed;
  right: 16px;
  bottom: 36px;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 4px;
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--mall-shadow);
}

.floating-toolbar__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 52px;
  height: 52px;
  border-radius: 10px;
  color: var(--mall-ink-2);
  font-size: 10px;
  transition:
    background-color 0.18s var(--mall-ease),
    color 0.18s var(--mall-ease);
}

.floating-toolbar__item:hover {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.floating-toolbar__item--top {
  opacity: 0;
  visibility: hidden;
  transform: translateY(6px);
  transition:
    opacity 0.2s var(--mall-ease),
    transform 0.2s var(--mall-ease),
    visibility 0.2s;
}

.floating-toolbar__item--top.is-visible {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

@media (max-width: 768px) {
  .floating-toolbar {
    right: 10px;
    bottom: 18px;
    border-radius: 12px;
  }

  .floating-toolbar__item {
    width: 44px;
    height: 44px;
  }

  .floating-toolbar__item span {
    display: none;
  }
}
</style>
