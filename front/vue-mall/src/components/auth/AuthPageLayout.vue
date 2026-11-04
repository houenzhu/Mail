<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  asideLabel: { type: String, default: '' },
})
</script>

<template>
  <div class="auth-page">
    <header class="auth-header">
      <div class="mall-container auth-header__inner">
        <RouterLink class="auth-brand" :to="{ name: 'home' }" aria-label="好物集商城首页">
          <span class="auth-brand__mark" aria-hidden="true">
            <span class="auth-brand__dot" />
            <span class="auth-brand__arc" />
          </span>
          <span class="auth-brand__text">
            <strong>好物集</strong>
            <small>GOODMALL</small>
          </span>
        </RouterLink>

        <div class="auth-header__aside">
          <slot name="header-aside" />
        </div>
      </div>
    </header>

    <main class="auth-main">
      <div class="mall-container auth-layout">
        <section class="auth-intro" :aria-label="asideLabel || undefined">
          <slot name="intro" />
        </section>
        <slot />
      </div>
    </main>

    <slot name="footer" />
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background:
    radial-gradient(1100px 520px at 8% -10%, #ffe7da 0%, rgba(255, 231, 218, 0) 62%),
    radial-gradient(900px 520px at 96% 6%, #eef1f6 0%, rgba(238, 241, 246, 0) 58%),
    var(--mall-bg);
}

.auth-header {
  border-bottom: 1px solid rgba(31, 31, 31, 0.06);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
}

.auth-header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 76px;
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.auth-brand__mark {
  position: relative;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--mall-brand);
  overflow: hidden;
}

.auth-brand__dot {
  position: absolute;
  top: 11px;
  left: 11px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #fff;
}

.auth-brand__arc {
  position: absolute;
  right: 4px;
  bottom: -6px;
  width: 23px;
  height: 23px;
  border: 2px solid rgba(255, 255, 255, 0.85);
  border-radius: 50%;
}

.auth-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.auth-brand__text strong {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.auth-brand__text small {
  margin-top: 4px;
  color: var(--mall-ink-3);
  font-size: 9px;
  letter-spacing: 0.32em;
}

.auth-header__aside {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.auth-header__aside :deep(a:hover) {
  color: var(--mall-brand);
}

.auth-main {
  flex: 1;
  display: flex;
  align-items: center;
  padding-block: 44px 52px;
}

.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  align-items: center;
  gap: 52px;
}

.auth-intro :deep(.auth-intro__eyebrow) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--mall-brand);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.auth-intro :deep(h1) {
  margin-top: 16px;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.32;
  letter-spacing: -0.02em;
}

.auth-intro :deep(.auth-intro__desc) {
  max-width: 430px;
  margin-top: 16px;
  color: var(--mall-ink-2);
  font-size: 14px;
  line-height: 1.85;
}

.auth-intro :deep(.auth-intro__points) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 24px;
}

.auth-intro :deep(.auth-intro__points li) {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.auth-intro :deep(.auth-intro__points svg) {
  color: var(--mall-brand);
}

.auth-intro :deep(.auth-intro__tip) {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 24px;
  padding: 8px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--mall-ink-3);
  font-size: 12px;
}

.auth-intro :deep(.auth-intro__tip strong) {
  color: var(--mall-ink);
  font-weight: 600;
}

@media (max-width: 980px) {
  .auth-layout {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .auth-intro :deep(h1) {
    font-size: 28px;
  }

  .auth-intro :deep(.auth-intro__desc),
  .auth-intro :deep(.auth-intro__points) {
    display: none;
  }

  .auth-main {
    align-items: flex-start;
    padding-block: 26px 40px;
  }
}

@media (max-width: 560px) {
  .auth-header__inner {
    min-height: 62px;
  }

  .auth-header__aside {
    display: none;
  }

  .auth-intro :deep(h1) {
    font-size: 23px;
  }
}
</style>