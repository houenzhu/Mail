<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  icon: { type: String, default: 'grid' },
  title: { type: String, default: '这里还空空的' },
  description: { type: String, default: '' },
})
</script>

<template>
  <div class="empty-state">
    <span class="empty-state__icon">
      <AppIcon :name="icon" :size="26" />
    </span>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 56px 24px;
  text-align: center;
}

.empty-state__icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
  margin-bottom: 4px;
}

.empty-state__title {
  font-size: 15px;
  font-weight: 600;
}

.empty-state__desc {
  max-width: 360px;
  color: var(--mall-ink-3);
  font-size: 13px;
}

.empty-state__action {
  margin-top: 8px;
}
</style>
