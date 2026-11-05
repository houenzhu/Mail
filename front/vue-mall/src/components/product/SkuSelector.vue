<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { canBuySku, buildSpecGroups, specMatches, specOfSku } from '@/utils/spec'

const props = defineProps({
  skus: { type: Array, default: () => [] },
  modelValue: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue'])

const specGroups = computed(() => buildSpecGroups(props.skus))
const hasSpec = computed(() => specGroups.value.length > 0)
const simpleMode = computed(() => !hasSpec.value && props.skus.length > 1)
const selection = ref({})

function resetSelection() {
  const current = props.modelValue?.id
    ? specOfSku(props.modelValue)
    : {}
  selection.value = current
  if (hasSpec.value) {
    const matched = props.skus.find((sku) => specMatches(sku, current) && canBuySku(sku))
    if (matched && matched.id !== props.modelValue?.id) emit('update:modelValue', matched)
    if (!matched) emit('update:modelValue', null)
    return
  }
  if (props.skus.length === 1) {
    emit('update:modelValue', canBuySku(props.skus[0]) ? props.skus[0] : null)
  }
}

watch(
  () => props.skus.map((sku) => `${sku.id}:${sku.status}:${sku.available}`).join('|'),
  resetSelection,
  { immediate: true },
)


// 父组件自动选中或规格变化后，把完整 SKU 回填到本地选择，避免后续点选丢失已选维度
watch(
  () => props.modelValue?.id,
  (id) => {
    if (!id) return
    const sku = props.skus.find((item) => item.id === id)
    if (sku) selection.value = specOfSku(sku)
  },
)
const selectedSkuId = computed(() => props.modelValue?.id || null)

const currentSpecLabel = computed(() => {
  if (!hasSpec.value) return props.modelValue?.name || ''
  const entries = Object.entries(selection.value).filter(([, value]) => value)
  return entries.length ? entries.map(([key, value]) => `${key}：${value}`).join(' / ') : '请选择规格'
})

function isOptionAvailable(groupKey, value) {
  const next = { ...selection.value, [groupKey]: value }
  return props.skus.some((sku) => specMatches(sku, next) && canBuySku(sku))
}

function pick(groupKey, value) {
  if (!isOptionAvailable(groupKey, value)) return
  const next = { ...selection.value, [groupKey]: value }
  selection.value = next
  const matched = props.skus.find((sku) => specMatches(sku, next))
  const complete = specGroups.value.every((group) => next[group.key])
  if (complete) {
    emit('update:modelValue', matched && canBuySku(matched) ? matched : null)
    return
  }
  emit('update:modelValue', null)
}

function pickSimple(sku) {
  if (!canBuySku(sku)) return
  emit('update:modelValue', sku)
}
</script>

<template>
  <div class="sku-selector">
    <template v-if="hasSpec">
      <div v-for="group in specGroups" :key="group.key" class="sku-group">
        <p class="sku-group__label">{{ group.label }}</p>
        <div class="sku-group__options" role="group" :aria-label="group.label">
          <button
            v-for="option in group.options"
            :key="option.value"
            type="button"
            class="sku-option"
            :class="{
              'is-active': selection[group.key] === option.value,
              'is-unavailable': !isOptionAvailable(group.key, option.value),
            }"
            :aria-pressed="selection[group.key] === option.value"
            :disabled="!isOptionAvailable(group.key, option.value)"
            @click="pick(group.key, option.value)"
          >
            {{ option.value }}
            <AppIcon
              v-if="selection[group.key] === option.value"
              name="check"
              :size="12"
            />
          </button>
        </div>
      </div>
      <p class="sku-summary" aria-live="polite">{{ currentSpecLabel }}</p>
    </template>

    <div v-else-if="simpleMode" class="sku-group">
      <p class="sku-group__label">选择规格</p>
      <div class="sku-group__options" role="group" aria-label="选择规格">
        <button
          v-for="sku in skus"
          :key="sku.id"
          type="button"
          class="sku-option sku-option--wide"
          :class="{
            'is-active': selectedSkuId === sku.id,
            'is-unavailable': !canBuySku(sku),
          }"
          :disabled="!canBuySku(sku)"
          :aria-pressed="selectedSkuId === sku.id"
          @click="pickSimple(sku)"
        >
          {{ sku.name }}
          <em v-if="sku.available === 0">售罄</em>
        </button>
      </div>
    </div>

    <p v-else-if="skus.length === 1" class="sku-summary">
      {{ skus[0].name }}
      <span v-if="!canBuySku(skus[0])" class="sku-summary__danger">（暂时不可购买）</span>
    </p>
  </div>
</template>

<style scoped>
.sku-selector {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sku-group__label {
  margin-bottom: 8px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.sku-group__options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sku-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 52px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 8px;
  background: #fff;
  color: var(--mall-ink);
  font-size: 13px;
  justify-content: center;
  transition:
    border-color 0.15s var(--mall-ease),
    color 0.15s var(--mall-ease),
    background-color 0.15s var(--mall-ease);
}

.sku-option:hover:not(:disabled) {
  border-color: var(--mall-brand);
  color: var(--mall-brand);
}

.sku-option.is-active {
  border-color: var(--mall-brand);
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
  font-weight: 600;
}

.sku-option.is-unavailable {
  color: var(--mall-ink-4);
  background: var(--mall-surface-2);
  border-style: dashed;
  cursor: not-allowed;
  text-decoration: line-through;
}

.sku-option--wide {
  min-width: 120px;
  justify-content: space-between;
}

.sku-option--wide em {
  color: var(--mall-ink-4);
  font-size: 11px;
  font-style: normal;
  text-decoration: none;
}

.sku-summary {
  color: var(--mall-ink-3);
  font-size: 12px;
}

.sku-summary__danger {
  color: var(--mall-brand);
}
</style>
