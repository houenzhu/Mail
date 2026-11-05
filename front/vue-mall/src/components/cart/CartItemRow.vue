<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import QuantityStepper from '@/components/product/QuantityStepper.vue'
import { formatPrice } from '@/utils/format'
import { formatSpec } from '@/utils/spec'
import { inferImageKey } from '@/utils/media'

const props = defineProps({
  item: { type: Object, required: true },
  busy: { type: Boolean, default: false },
  removing: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle', 'quantity', 'remove'])

const maxQuantity = computed(() => {
  if (props.item.invalid) return 1
  if (props.item.available === null) return 99
  return Math.max(1, Math.min(99, props.item.available))
})

const subtotal = computed(() => props.item.price * props.item.quantity)
const imageKey = computed(() => inferImageKey(props.item.productName))
const specText = computed(() => formatSpec(props.item.specJson, props.item.skuName))
const detailPath = computed(() => (
  props.item.productId ? `/product/${props.item.productId}` : ''
))

function toggle(event) {
  if (props.item.invalid || props.busy) return
  emit('toggle', event.target.checked)
}
</script>

<template>
  <article class="cart-row" :class="{ 'is-invalid': item.invalid, 'is-busy': busy }">
    <label class="cart-row__check" :class="{ 'is-disabled': item.invalid }">
      <input
        type="checkbox"
        :checked="item.selected && !item.invalid"
        :disabled="item.invalid || busy"
        :aria-label="`选择 ${item.productName}`"
        @change="toggle"
      />
      <span class="cart-row__checkmark" aria-hidden="true">
        <AppIcon v-if="item.selected && !item.invalid" name="check" :size="13" :stroke-width="2.4" />
      </span>
    </label>

    <RouterLink v-if="detailPath" class="cart-row__media" :to="detailPath">
      <SmartImage
        :src="item.image"
        :alt="item.productName"
        :fallback-key="imageKey"
        ratio="1"
      />
      <span v-if="item.invalid" class="cart-row__invalid-mask">已失效</span>
    </RouterLink>
    <div v-else class="cart-row__media">
      <SmartImage :src="item.image" :alt="item.productName" :fallback-key="imageKey" ratio="1" />
      <span v-if="item.invalid" class="cart-row__invalid-mask">已失效</span>
    </div>

    <div class="cart-row__info">
      <component
        :is="detailPath ? RouterLink : 'p'"
        :to="detailPath || undefined"
        class="cart-row__name"
      >
        {{ item.productName }}
      </component>
      <p class="cart-row__spec">{{ specText }}</p>
      <p v-if="item.invalid" class="cart-row__invalid-reason">
        <AppIcon name="close" :size="12" />
        {{ item.invalidReason || '商品已失效' }}
      </p>
      <p v-else-if="item.available === null" class="cart-row__stock">
        库存待确认，结算时以实际库存为准
      </p>
      <p v-else-if="item.available <= 5" class="cart-row__stock cart-row__stock--low">
        仅剩 {{ item.available }} 件
      </p>
    </div>

    <div class="cart-row__price">
      <strong>¥{{ formatPrice(item.price) }}</strong>
      <del v-if="item.marketPrice > item.price">¥{{ formatPrice(item.marketPrice) }}</del>
    </div>

    <div class="cart-row__quantity">
      <QuantityStepper
        :model-value="item.quantity"
        :max="maxQuantity"
        :disabled="item.invalid || busy"
        size="sm"
        :label="`${item.productName} 数量`"
        @change="emit('quantity', $event)"
      />
      <span v-if="!item.invalid && item.available !== null && item.quantity >= item.available" class="cart-row__limit">
        已达库存上限
      </span>
    </div>

    <div class="cart-row__subtotal">
      <strong>¥{{ formatPrice(subtotal) }}</strong>
    </div>

    <button
      type="button"
      class="cart-row__remove"
      :disabled="removing"
      :aria-label="`删除 ${item.productName}`"
      @click="emit('remove')"
    >
      <AppIcon :name="removing ? 'refresh' : 'close'" :size="14" />
      <span>{{ removing ? '删除中' : '删除' }}</span>
    </button>
  </article>
</template>

<style scoped>
.cart-row {
  display: grid;
  grid-template-columns: 26px 96px minmax(0, 1fr) 110px 132px 108px 62px;
  align-items: center;
  gap: 16px;
  padding: 18px 16px;
  border-bottom: 1px solid var(--mall-line);
  background: #fff;
  transition: background-color 0.2s var(--mall-ease);
}

.cart-row:last-child {
  border-bottom: 0;
}

.cart-row:hover {
  background: #fffdfc;
}

.cart-row.is-busy {
  opacity: 0.72;
}

.cart-row.is-invalid {
  background: var(--mall-surface-2);
}

.cart-row__check {
  position: relative;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  cursor: pointer;
}

.cart-row__check.is-disabled {
  cursor: not-allowed;
}

.cart-row__check input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.cart-row__checkmark {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 50%;
  background: #fff;
  color: #fff;
  transition:
    background-color 0.15s var(--mall-ease),
    border-color 0.15s var(--mall-ease);
}

.cart-row__check input:checked + .cart-row__checkmark {
  border-color: var(--mall-brand);
  background: var(--mall-brand);
}

.cart-row__check input:focus-visible + .cart-row__checkmark {
  outline: 2px solid var(--mall-brand);
  outline-offset: 3px;
}

.cart-row__check input:disabled + .cart-row__checkmark {
  background: var(--mall-line);
  border-color: var(--mall-line);
}

.cart-row__media {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
  background: var(--mall-surface-2);
}

.cart-row__invalid-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.68);
  color: var(--mall-ink-2);
  font-size: 12px;
  font-weight: 600;
}

.cart-row__info {
  min-width: 0;
}

.cart-row__name {
  display: -webkit-box;
  overflow: hidden;
  color: var(--mall-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

a.cart-row__name:hover {
  color: var(--mall-brand);
}

.cart-row__spec {
  margin-top: 5px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.cart-row__invalid-reason {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 7px;
  color: #b7791f;
  font-size: 12px;
}

.cart-row__stock {
  margin-top: 7px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.cart-row__stock--low {
  color: var(--mall-brand);
}

.cart-row__price,
.cart-row__subtotal {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.cart-row__price strong,
.cart-row__subtotal strong {
  color: var(--mall-ink);
  font-weight: 600;
}

.cart-row__subtotal strong {
  color: var(--mall-brand);
  font-size: 15px;
}

.cart-row__price del {
  color: var(--mall-ink-4);
  font-size: 11px;
}

.cart-row__quantity {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.cart-row__limit {
  color: var(--mall-ink-3);
  font-size: 11px;
}

.cart-row__remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  height: 30px;
  border-radius: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
  transition:
    background-color 0.15s var(--mall-ease),
    color 0.15s var(--mall-ease);
}

.cart-row__remove:hover:not(:disabled) {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.cart-row__remove:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 960px) {
  .cart-row {
    grid-template-columns: 24px 84px minmax(0, 1fr) 92px 120px;
    grid-template-areas:
      'check media info info info'
      'check media price quantity subtotal';
    row-gap: 10px;
    padding: 14px 12px;
  }

  .cart-row__check { grid-area: check; align-self: start; margin-top: 4px; }
  .cart-row__media { grid-area: media; }
  .cart-row__info { grid-area: info; }
  .cart-row__price { grid-area: price; }
  .cart-row__quantity { grid-area: quantity; }
  .cart-row__subtotal { grid-area: subtotal; align-items: flex-end; }
  .cart-row__remove { grid-column: 5; grid-row: 1; justify-self: end; align-self: start; }
}

@media (max-width: 640px) {
  .cart-row {
    grid-template-columns: 22px 72px minmax(0, 1fr);
    grid-template-areas:
      'check media info'
      'check media price'
      'check media quantity'
      'subtotal subtotal remove';
    gap: 8px 10px;
    padding: 12px 10px;
  }

  .cart-row__check { align-self: start; margin-top: 4px; }
  .cart-row__media { align-self: start; }
  .cart-row__price,
  .cart-row__subtotal { flex-direction: row; align-items: baseline; gap: 6px; }
  .cart-row__subtotal {
    justify-content: flex-start;
    padding-top: 8px;
    border-top: 1px dashed var(--mall-line);
  }
  .cart-row__remove {
    align-self: center;
    justify-self: end;
    padding-inline: 6px;
  }

  .cart-row__name { font-size: 13px; }
  .cart-row__spec { font-size: 11px; }
}
</style>