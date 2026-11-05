<script setup>
import { onBeforeUnmount, ref } from 'vue'
import AppIcon from './AppIcon.vue'

const toast = ref({ visible: false, message: '', tone: 'success' })
let timer = null

function dismiss() {
  toast.value.visible = false
  clearTimeout(timer)
}

function show(message, tone = 'success', duration = 2600) {
  clearTimeout(timer)
  toast.value = { visible: true, message, tone }
  timer = setTimeout(() => {
    toast.value.visible = false
  }, duration)
}

onBeforeUnmount(() => clearTimeout(timer))

defineExpose({ show, dismiss })
</script>

<template>
  <Transition name="toast">
    <div
      v-if="toast.visible"
      class="app-toast"
      :class="`app-toast--${toast.tone}`"
      role="status"
      aria-live="polite"
    >
      <AppIcon :name="toast.tone === 'error' ? 'close' : 'check'" :size="16" />
      <span>{{ toast.message }}</span>
      <button type="button" class="app-toast__close" aria-label="关闭提示" @click="dismiss">
        <AppIcon name="close" :size="13" />
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.app-toast {
  position: fixed;
  top: 22px;
  left: 50%;
  z-index: 120;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(92vw, 420px);
  padding: 10px 12px 10px 16px;
  border-radius: 999px;
  background: rgba(31, 31, 31, 0.92);
  color: #fff;
  font-size: 13px;
  box-shadow: var(--mall-shadow-lg);
  transform: translateX(-50%);
  backdrop-filter: blur(6px);
}

.app-toast--error {
  background: rgba(196, 42, 42, 0.94);
}

.app-toast__close {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: rgba(255, 255, 255, 0.72);
}

.app-toast__close:hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s var(--mall-ease),
    transform 0.2s var(--mall-ease);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}
</style>