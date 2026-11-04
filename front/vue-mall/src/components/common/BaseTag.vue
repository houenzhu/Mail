<script setup>
defineProps({
  tone: { type: String, default: 'neutral' },
  size: { type: String, default: 'md' },
  solid: { type: Boolean, default: false },
})
</script>

<template>
  <span class="base-tag" :class="[`base-tag--${tone}`, `base-tag--${size}`, { 'is-solid': solid }]">
    <slot />
  </span>
</template>

<style scoped>
.base-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 4px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.base-tag--sm {
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
}

.base-tag--md {
  height: 22px;
  padding: 0 8px;
  font-size: 12px;
}

.base-tag--brand {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.base-tag--neutral {
  background: #f2f2f2;
  color: var(--mall-ink-2);
}

.base-tag--success {
  background: rgba(26, 156, 98, 0.1);
  color: var(--mall-success);
}

.base-tag--dark {
  background: rgba(31, 31, 31, 0.06);
  color: var(--mall-ink);
}

.base-tag.is-solid.base-tag--brand {
  background: var(--mall-brand);
  color: #fff;
}

.base-tag.is-solid.base-tag--dark {
  background: rgba(31, 31, 31, 0.82);
  color: #fff;
}
</style>
