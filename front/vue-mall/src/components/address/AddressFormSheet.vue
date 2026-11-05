<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  address: { type: Object, default: null },
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'save'])

const form = ref(emptyForm())
const error = ref('')
const firstFieldRef = ref(null)

const title = computed(() => (props.address ? '编辑收货地址' : '新增收货地址'))

function emptyForm() {
  return { receiverName: '', receiverPhone: '', region: '', detailAddress: '', isDefault: false }
}

function reset() {
  form.value = props.address
    ? {
        receiverName: props.address.receiverName || '',
        receiverPhone: props.address.receiverPhone || '',
        region: props.address.region || '',
        detailAddress: props.address.detailAddress || '',
        isDefault: Boolean(props.address.isDefault),
      }
    : emptyForm()
  error.value = ''
}

watch(
  () => props.open,
  async (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (!open) return
    reset()
    await nextTick()
    firstFieldRef.value?.focus()
  },
)

watch(
  () => props.address,
  () => {
    if (props.open) reset()
  },
)

function onKeydown(event) {
  if (event.key === 'Escape' && props.open && !props.saving) emit('close')
}

if (typeof document !== 'undefined') {
  document.addEventListener('keydown', onKeydown)
  onBeforeUnmount(() => {
    document.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = ''
  })
}

function validate() {
  if (!form.value.receiverName.trim()) return '请填写收货人'
  if (!/^1[3-9]\d{9}$/.test(form.value.receiverPhone.trim())) return '请填写正确的 11 位手机号'
  if (!form.value.detailAddress.trim()) return '请填写详细地址'
  if (form.value.detailAddress.trim().length > 255) return '详细地址不能超过 255 个字符'
  return ''
}

function submit() {
  error.value = validate()
  if (error.value) return
  emit('save', {
    receiverName: form.value.receiverName.trim(),
    receiverPhone: form.value.receiverPhone.trim(),
    region: form.value.region.trim(),
    detailAddress: form.value.detailAddress.trim(),
    isDefault: form.value.isDefault,
  })
}

defineExpose({ setError: (message) => { error.value = message || '' } })
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div v-if="open" class="sheet-layer">
        <button class="sheet-mask" type="button" aria-label="关闭" @click="emit('close')" />
        <section class="sheet" role="dialog" aria-modal="true" aria-labelledby="address-form-title">
          <header class="sheet__head">
            <h2 id="address-form-title">{{ title }}</h2>
            <button type="button" class="sheet__close" aria-label="关闭" @click="emit('close')">
              <AppIcon name="close" :size="18" />
            </button>
          </header>

          <form class="sheet__body" @submit.prevent="submit">
            <label class="field">
              <span>收货人</span>
              <input ref="firstFieldRef" v-model="form.receiverName" maxlength="64" placeholder="请填写收货人姓名" />
            </label>
            <label class="field">
              <span>手机号</span>
              <input v-model="form.receiverPhone" inputmode="tel" maxlength="11" placeholder="用于配送联系" />
            </label>
            <label class="field">
              <span>所在地区</span>
              <input v-model="form.region" maxlength="120" placeholder="如：广东省广州市天河区" />
            </label>
            <label class="field">
              <span>详细地址</span>
              <textarea v-model="form.detailAddress" maxlength="255" rows="3" placeholder="街道、门牌号、楼层等" />
            </label>
            <label class="checkbox">
              <input v-model="form.isDefault" type="checkbox" />
              <span>设为默认地址</span>
            </label>
            <p v-if="error" class="sheet__error" role="alert">{{ error }}</p>
          </form>

          <footer class="sheet__actions">
            <BaseButton variant="ghost" :disabled="saving" @click="emit('close')">取消</BaseButton>
            <BaseButton :loading="saving" @click="submit">保存地址</BaseButton>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet-layer { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; justify-content: center; }
.sheet-mask { position: absolute; inset: 0; background: rgba(20, 20, 20, 0.38); backdrop-filter: blur(2px); }
.sheet {
  position: relative; z-index: 1; width: min(520px, 100vw); max-height: 92vh; overflow-y: auto;
  padding: 22px 24px 24px; background: #fff; border-radius: 18px 18px 0 0; box-shadow: var(--mall-shadow-lg);
}
.sheet__head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 14px; border-bottom: 1px solid var(--mall-line); }
.sheet__head h2 { font-size: 18px; font-weight: 800; }
.sheet__close { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; color: var(--mall-ink-3); }
.sheet__close:hover { background: var(--mall-surface-2); color: var(--mall-ink); }
.sheet__body { display: flex; flex-direction: column; gap: 14px; padding: 18px 0 6px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field > span { color: var(--mall-ink-2); font-size: 12px; }
.field input, .field textarea {
  width: 100%; padding: 10px 12px; border: 1px solid var(--mall-line); border-radius: 10px;
  font: inherit; color: var(--mall-ink); background: #fff; resize: vertical;
}
.field input:focus, .field textarea:focus { outline: none; border-color: var(--mall-brand); box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.04); }
.checkbox { display: inline-flex; align-items: center; gap: 8px; color: var(--mall-ink-2); font-size: 13px; }
.checkbox input { width: 16px; height: 16px; accent-color: var(--mall-brand); }
.sheet__error { margin: 0; color: #c53030; font-size: 12px; }
.sheet__actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 16px; }
.sheet-enter-active, .sheet-leave-active { transition: opacity 0.18s ease; }
.sheet-enter-from, .sheet-leave-to { opacity: 0; }
@media (min-width: 761px) {
  .sheet-layer { align-items: center; }
  .sheet { border-radius: 18px; }
}
</style>
