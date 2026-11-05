<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: 1 },
  min: { type: Number, default: 1 },
  max: { type: Number, default: 99 },
  disabled: { type: Boolean, default: false },
  size: { type: String, default: 'md' },
  label: { type: String, default: '购买数量' },
  inputId: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'change'])

const limit = computed(() => Math.max(props.min, Math.min(props.max, 99)))
const value = computed(() => {
  const num = Number(props.modelValue)
  if (!Number.isFinite(num)) return props.min
  return Math.min(limit.value, Math.max(props.min, Math.trunc(num)))
})
const atMin = computed(() => value.value <= props.min)
const atMax = computed(() => value.value >= limit.value)

function commit(next) {
  if (props.disabled) return
  const safe = Math.min(limit.value, Math.max(props.min, Math.trunc(Number(next) || props.min)))
  if (safe !== props.modelValue) emit('update:modelValue', safe)
  emit('change', safe)
}

function step(delta) {
  commit(value.value + delta)
}

function onInput(event) {
  const raw = event.target.value.replace(/\D/g, '')
  event.target.value = raw
  if (raw === '') return
  commit(Number(raw))
}

function onBlur(event) {
  event.target.value = String(value.value)
}
</script>

<template>
  <div
    class="quantity-stepper"
    :class="[`quantity-stepper--${size}`, { 'is-disabled': disabled }]"
    role="group"
    :aria-label="label"
  >
    <button
      type="button"
      class="quantity-stepper__button"
      :disabled="disabled || atMin"
      aria-label="减少数量"
      @click="step(-1)"
    >
      −
    </button>
    <input
      :id="inputId || undefined"
      class="quantity-stepper__input"
      type="text"
      inputmode="numeric"
      pattern="[0-9]*"
      :value="value"
      :disabled="disabled"
      :aria-label="label"
      @input="onInput"
      @blur="onBlur"
      @keydown.up.prevent="step(1)"
      @keydown.down.prevent="step(-1)"
    />
    <button
      type="button"
      class="quantity-stepper__button"
      :disabled="disabled || atMax"
      aria-label="增加数量"
      @click="step(1)"
    >
      +
    </button>
  </div>
</template>

<style scoped>
.quantity-stepper {
  display: inline-flex;
  align-items: center;
  overflow: hidden;
  height: 34px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 8px;
  background: #fff;
}

.quantity-stepper__button {
  width: 32px;
  height: 100%;
  color: var(--mall-ink-2);
  font-size: 16px;
  line-height: 1;
  transition: background-color 0.15s var(--mall-ease);
}

.quantity-stepper__button:hover:not(:disabled) {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.quantity-stepper__button:disabled {
  color: var(--mall-ink-4);
  cursor: not-allowed;
}

.quantity-stepper__input {
  width: 44px;
  height: 100%;
  border: 0;
  border-inline: 1px solid var(--mall-line);
  background: #fff;
  color: var(--mall-ink);
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  outline: none;
}

.quantity-stepper__input:focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid var(--mall-brand);
  outline-offset: -2px;
}

.quantity-stepper--sm {
  height: 30px;
}

.quantity-stepper--sm .quantity-stepper__button {
  width: 28px;
}

.quantity-stepper--sm .quantity-stepper__input {
  width: 38px;
  font-size: 12px;
}

.quantity-stepper.is-disabled {
  background: var(--mall-surface-2);
}

.quantity-stepper.is-disabled .quantity-stepper__input {
  background: var(--mall-surface-2);
}
</style>