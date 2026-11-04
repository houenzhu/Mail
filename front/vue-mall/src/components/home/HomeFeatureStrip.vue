<script setup>
import AppIcon from '@/components/common/AppIcon.vue'
import { servicePromises } from '@/data/homeContent'
</script>

<template>
  <section class="feature-strip" aria-label="服务保障">
    <div
      v-for="item in servicePromises"
      :key="item.id"
      class="feature-strip__item"
    >
      <span class="feature-strip__icon">
        <AppIcon :name="item.icon" :size="22" />
      </span>
      <div class="feature-strip__text">
        <strong>{{ item.title }}</strong>
        <span>{{ item.desc }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.feature-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin-top: 18px;
  border: 1px solid var(--mall-line);
  border-radius: var(--mall-radius);
  background: var(--mall-line);
  overflow: hidden;
}

.feature-strip__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #fff;
}

.feature-strip__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
  flex-shrink: 0;
}

.feature-strip__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.feature-strip__text strong {
  font-size: 13px;
  font-weight: 600;
}

.feature-strip__text span {
  overflow: hidden;
  color: var(--mall-ink-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .feature-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .feature-strip__item {
    gap: 10px;
    padding: 12px 12px;
  }

  .feature-strip__icon {
    width: 32px;
    height: 32px;
  }

  .feature-strip__text strong {
    font-size: 12px;
  }

  .feature-strip__text span {
    font-size: 10px;
  }
}
</style>
