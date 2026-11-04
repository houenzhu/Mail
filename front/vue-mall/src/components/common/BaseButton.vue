<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  to: { type: [String, Object], default: '' },
  variant: { type: String, default: 'primary' },
  size: { type: String, default: 'md' },
  block: { type: Boolean, default: false },
  type: { type: String, default: 'button' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})

const tag = computed(() => (props.to && !props.disabled ? RouterLink : 'button'))

const classes = computed(() => [
  'base-button',
  `base-button--${props.variant}`,
  `base-button--${props.size}`,
  { 'is-block': props.block, 'is-disabled': props.disabled, 'is-loading': props.loading },
])
</script>

<template>
  <component
    :is="tag"
    :to="tag === 'button' ? undefined : to"
    :type="tag === 'button' ? type : undefined"
    :disabled="tag === 'button' ? disabled || loading : undefined"
    :aria-disabled="disabled || loading ? 'true' : undefined"
    :class="classes"
  >
    <span v-if="loading" class="base-button__spinner" aria-hidden="true" />
    <slot />
  </component>
</template>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0.01em;
  white-space: nowrap;
  transition:
    background-color 0.2s var(--mall-ease),
    color 0.2s var(--mall-ease),
    box-shadow 0.2s var(--mall-ease),
    transform 0.2s var(--mall-ease);
}

.base-button:not(.is-disabled):active {
  transform: translateY(1px);
}

.base-button--sm {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
}

.base-button--md {
  height: 40px;
  padding: 0 20px;
  font-size: 14px;
}

.base-button--lg {
  height: 48px;
  padding: 0 28px;
  font-size: 16px;
}

.base-button--primary {
  background: var(--mall-brand);
  color: #fff;
  box-shadow: 0 8px 18px rgba(255, 80, 0, 0.22);
}

.base-button--primary:hover {
  background: var(--mall-brand-deep);
  box-shadow: 0 10px 24px rgba(255, 80, 0, 0.3);
}

.base-button--secondary {
  background: var(--mall-ink);
  color: #fff;
}

.base-button--secondary:hover {
  background: #000;
}

.base-button--ghost {
  background: #fff;
  color: var(--mall-ink);
  border: 1px solid var(--mall-line-strong);
}

.base-button--ghost:hover {
  border-color: var(--mall-brand);
  color: var(--mall-brand);
}

.base-button--soft {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.base-button--soft:hover {
  background: #ffe3d6;
}

.base-button--text {
  height: auto;
  padding: 0;
  color: var(--mall-ink-2);
  font-weight: 500;
}

.base-button--text:hover {
  color: var(--mall-brand);
}

.is-block {
  width: 100%;
}

.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.base-button__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: base-spin 0.7s linear infinite;
}

@keyframes base-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
