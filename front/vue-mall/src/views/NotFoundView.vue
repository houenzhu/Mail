<script setup>
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
</script>

<template>
  <div class="not-found">
    <SiteHeader />
    <main class="mall-container not-found__main">
      <div class="not-found__card">
        <span class="not-found__code">404</span>
        <h1>这条小路暂时没有好物</h1>
        <p>页面可能已经下架或地址输入有误，回首页继续逛逛吧。</p>
        <div class="not-found__actions">
          <BaseButton :to="{ name: 'home' }">
            <AppIcon name="home" :size="16" />
            回到首页
          </BaseButton>
          <BaseButton variant="ghost" :to="{ name: 'search', query: { q: '' } }">
            浏览商品
          </BaseButton>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.not-found__main {
  flex: 1;
  display: grid;
  place-items: center;
  padding-block: 60px;
}

.not-found__card {
  width: min(100%, 560px);
  padding: 48px 32px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  text-align: center;
  box-shadow: var(--mall-shadow-sm);
}

.not-found__code {
  color: var(--mall-brand);
  font-size: 64px;
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1;
}

.not-found__card h1 {
  margin-top: 14px;
  font-size: 22px;
  font-weight: 700;
}

.not-found__card p {
  margin-top: 8px;
  color: var(--mall-ink-3);
  font-size: 13px;
}

.not-found__actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 26px;
}
</style>
