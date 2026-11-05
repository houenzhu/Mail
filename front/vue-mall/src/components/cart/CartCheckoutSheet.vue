<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import { formatPrice } from '@/utils/format'
import { inferImageKey } from '@/utils/media'

const props = defineProps({
  open: { type: Boolean, default: false },
  checkout: { type: Object, default: null },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'confirm'])

const closeButtonRef = ref(null)

const items = computed(() => props.checkout?.items || [])
const totalQuantity = computed(() => Number(props.checkout?.totalQuantity) || 0)
const payAmount = computed(() => Number(props.checkout?.payAmount) || 0)

function onKeydown(event) {
  if (event.key === 'Escape' && props.open) emit('close')
}

watch(
  () => props.open,
  async (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return
    await nextTick()
    closeButtonRef.value?.focus()
  },
)

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="checkout-sheet">
      <div v-if="open" class="checkout-layer">
        <button
          class="checkout-layer__mask"
          type="button"
          aria-label="关闭结算预览"
          @click="emit('close')"
        />
        <section
          class="checkout-sheet"
          role="dialog"
          aria-modal="true"
          aria-labelledby="checkout-title"
        >
          <header class="checkout-sheet__head">
            <div>
              <h2 id="checkout-title">结算预览</h2>
              <p>商品与金额已按实时价格核对</p>
            </div>
            <button
              ref="closeButtonRef"
              type="button"
              class="checkout-sheet__close"
              aria-label="关闭"
              @click="emit('close')"
            >
              <AppIcon name="close" :size="18" />
            </button>
          </header>

          <ul class="checkout-list">
            <li v-for="item in items" :key="item.skuId" class="checkout-item">
              <SmartImage
                class="checkout-item__media"
                :src="item.image"
                :alt="item.productName"
                :fallback-key="inferImageKey(item.productName)"
                ratio="1"
              />
              <div class="checkout-item__info">
                <p class="checkout-item__name">{{ item.productName }}</p>
                <p class="checkout-item__spec">{{ item.skuName }}</p>
              </div>
              <div class="checkout-item__price">
                <strong>¥{{ formatPrice(item.price) }}</strong>
                <span>× {{ item.quantity }}</span>
              </div>
            </li>
          </ul>

          <dl class="checkout-total">
            <div>
              <dt>商品件数</dt>
              <dd>{{ totalQuantity }} 件</dd>
            </div>
            <div class="checkout-total__amount">
              <dt>应付金额</dt>
              <dd>¥{{ formatPrice(payAmount) }}</dd>
            </div>
          </dl>

          <p class="checkout-note">
            <AppIcon name="truck" :size="15" />
            地址填写与在线支付将在下一阶段接入，本次仅展示结算预览。
          </p>

          <div class="checkout-sheet__actions">
            <BaseButton variant="ghost" @click="emit('close')">返回修改</BaseButton>
            <BaseButton :loading="loading" @click="emit('confirm')">确认商品</BaseButton>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.checkout-layer {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  justify-content: flex-end;
}

.checkout-layer__mask {
  position: absolute;
  inset: 0;
  background: rgba(20, 20, 20, 0.38);
  backdrop-filter: blur(2px);
}

.checkout-sheet {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: min(440px, 100vw);
  height: 100%;
  padding: 26px 24px;
  background: #fff;
  box-shadow: var(--mall-shadow-lg);
  overflow-y: auto;
}

.checkout-sheet__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--mall-line);
}

.checkout-sheet__head h2 {
  font-size: 20px;
  font-weight: 800;
}

.checkout-sheet__head p {
  margin-top: 4px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.checkout-sheet__close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--mall-surface-2);
  color: var(--mall-ink-2);
}

.checkout-sheet__close:hover {
  background: var(--mall-brand-soft);
  color: var(--mall-brand);
}

.checkout-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 0;
}

.checkout-item {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.checkout-item__media {
  border-radius: 8px;
}

.checkout-item__name {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.checkout-item__spec {
  margin-top: 4px;
  color: var(--mall-ink-3);
  font-size: 11px;
}

.checkout-item__price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  font-size: 12px;
}

.checkout-item__price strong {
  color: var(--mall-brand);
  font-size: 14px;
}

.checkout-item__price span {
  color: var(--mall-ink-3);
}

.checkout-total {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: var(--mall-radius);
  background: var(--mall-surface-2);
}

.checkout-total > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.checkout-total__amount {
  padding-top: 10px;
  border-top: 1px dashed var(--mall-line-strong);
}

.checkout-total__amount dd {
  color: var(--mall-brand);
  font-size: 24px;
  font-weight: 800;
}

.checkout-note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #fff8ec;
  color: #9a6a18;
  font-size: 12px;
  line-height: 1.7;
}

.checkout-note :deep(svg) {
  margin-top: 2px;
  flex-shrink: 0;
}

.checkout-sheet__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: auto;
  padding-top: 22px;
}

.checkout-sheet-enter-active,
.checkout-sheet-leave-active {
  transition: opacity 0.24s var(--mall-ease);
}

.checkout-sheet-enter-active .checkout-sheet,
.checkout-sheet-leave-active .checkout-sheet {
  transition: transform 0.28s var(--mall-ease);
}

.checkout-sheet-enter-from,
.checkout-sheet-leave-to {
  opacity: 0;
}

.checkout-sheet-enter-from .checkout-sheet,
.checkout-sheet-leave-to .checkout-sheet {
  transform: translateX(100%);
}

@media (max-width: 560px) {
  .checkout-layer {
    align-items: flex-end;
  }

  .checkout-sheet {
    width: 100%;
    height: auto;
    max-height: 88vh;
    border-radius: 20px 20px 0 0;
    padding: 20px 18px calc(18px + env(safe-area-inset-bottom));
  }

  .checkout-sheet-enter-from .checkout-sheet,
  .checkout-sheet-leave-to .checkout-sheet {
    transform: translateY(100%);
  }
}
</style>