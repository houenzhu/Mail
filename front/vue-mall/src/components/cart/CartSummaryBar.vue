<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { formatPrice } from '@/utils/format'

const props = defineProps({
  allSelected: { type: Boolean, default: false },
  selectedCount: { type: Number, default: 0 },
  selectedAmount: { type: Number, default: 0 },
  validCount: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  selecting: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle-all', 'checkout'])

const canCheckout = computed(() => props.selectedCount > 0 && !props.loading)

function toggleAll(event) {
  emit('toggle-all', event.target.checked)
}
</script>

<template>
  <div class="cart-summary" role="region" aria-label="购物车结算">
    <label class="cart-summary__all" :class="{ 'is-disabled': !validCount }">
      <input
        type="checkbox"
        :checked="allSelected"
        :disabled="!validCount || selecting || loading"
        aria-label="全选有效商品"
        @change="toggleAll"
      />
      <span class="cart-summary__checkmark" aria-hidden="true">
        <AppIcon v-if="allSelected" name="check" :size="13" :stroke-width="2.4" />
      </span>
      全选
    </label>

    <div class="cart-summary__actions">
      <RouterLink class="cart-summary__continue" :to="{ name: 'search', query: { q: '' } }">
        继续购物
      </RouterLink>
      <p class="cart-summary__count">
        已选 <strong>{{ selectedCount }}</strong> 件
      </p>
      <p class="cart-summary__total">
        合计：
        <strong>¥{{ formatPrice(selectedAmount) }}</strong>
      </p>
      <BaseButton
        class="cart-summary__submit"
        size="lg"
        :disabled="!canCheckout"
        :loading="loading"
        @click="emit('checkout')"
      >
        {{ canCheckout ? '去结算' : '请选择商品' }}
      </BaseButton>
    </div>
  </div>
</template>

<style scoped>
.cart-summary {
  position: sticky;
  bottom: 0;
  z-index: 12;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 14px;
  padding: 14px 20px;
  border: 1px solid var(--mall-line);
  border-radius: var(--mall-radius);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -4px 18px rgba(31, 31, 31, 0.05);
  backdrop-filter: blur(8px);
}

.cart-summary__all {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--mall-ink-2);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.cart-summary__all.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cart-summary__all input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.cart-summary__checkmark {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 1px solid var(--mall-line-strong);
  border-radius: 50%;
  background: #fff;
  color: #fff;
}

.cart-summary__all input:checked + .cart-summary__checkmark {
  border-color: var(--mall-brand);
  background: var(--mall-brand);
}

.cart-summary__all input:focus-visible + .cart-summary__checkmark {
  outline: 2px solid var(--mall-brand);
  outline-offset: 3px;
}

.cart-summary__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  min-width: 0;
}

.cart-summary__continue {
  color: var(--mall-ink-2);
  font-size: 13px;
}

.cart-summary__continue:hover {
  color: var(--mall-brand);
}

.cart-summary__count {
  color: var(--mall-ink-2);
  font-size: 13px;
  white-space: nowrap;
}

.cart-summary__count strong {
  color: var(--mall-brand);
}

.cart-summary__total {
  color: var(--mall-ink-2);
  font-size: 13px;
  white-space: nowrap;
}

.cart-summary__total strong {
  color: var(--mall-brand);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.cart-summary__submit {
  min-width: 132px;
}

@media (max-width: 760px) {
  .cart-summary {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    margin: 0;
    border-radius: 0;
    border-inline: 0;
    border-bottom: 0;
    padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
    gap: 8px;
  }

  .cart-summary__continue,
  .cart-summary__count {
    display: none;
  }

  .cart-summary__actions {
    gap: 10px;
  }

  .cart-summary__total strong {
    font-size: 19px;
  }

  .cart-summary__submit {
    min-width: 104px;
  }
}

@media (max-width: 420px) {
  .cart-summary__all {
    font-size: 12px;
    gap: 5px;
  }

  .cart-summary__total {
    font-size: 12px;
  }

  .cart-summary__total strong {
    font-size: 17px;
  }

  .cart-summary__submit {
    min-width: 92px;
    padding-inline: 14px;
  }
}
</style>