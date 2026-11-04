<script setup>
import { computed, ref, watch } from 'vue'
import { fallbackFor, resolveImage } from '@/utils/media'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  fallbackKey: { type: String, default: 'default' },
  ratio: { type: [String, Number], default: 1 },
  eager: { type: Boolean, default: false },
  tone: { type: String, default: '' },
  position: { type: String, default: 'center' },
})

const emit = defineEmits(['load', 'error'])

const loaded = ref(false)
const failed = ref(false)
const currentSrc = ref(resolveImage(props.src, props.fallbackKey))

watch(
  () => [props.src, props.fallbackKey],
  ([src, key]) => {
    loaded.value = false
    failed.value = false
    currentSrc.value = resolveImage(src, key)
  },
)

const aspectRatio = computed(() => {
  if (typeof props.ratio === 'number') return String(props.ratio)
  return props.ratio || '1'
})

const wrapperStyle = computed(() => ({
  aspectRatio: aspectRatio.value,
  '--image-tone': props.tone || undefined,
  '--image-position': props.position,
}))

function handleLoad() {
  loaded.value = true
  emit('load')
}

function handleError() {
  if (failed.value) return
  failed.value = true
  currentSrc.value = fallbackFor(props.fallbackKey)
  emit('error')
}
</script>

<template>
  <div
    class="smart-image"
    :class="{ 'is-loaded': loaded || failed }"
    :style="wrapperStyle"
  >
    <div v-if="!loaded" class="smart-image__placeholder skeleton" aria-hidden="true" />
    <img
      class="smart-image__img"
      :class="{ 'is-visible': loaded }"
      :src="currentSrc"
      :alt="alt"
      :loading="eager ? 'eager' : 'lazy'"
      :fetchpriority="eager ? 'high' : 'auto'"
      decoding="async"
      @load="handleLoad"
      @error="handleError"
    />
  </div>
</template>

<style scoped>
.smart-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--image-tone, #f4f1ed);
  isolation: isolate;
}

.smart-image__placeholder {
  position: absolute;
  inset: 0;
}

.smart-image__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: var(--image-position, center);
  opacity: 0;
  transform: scale(1.015);
  transition:
    opacity 0.4s var(--mall-ease),
    transform 0.6s var(--mall-ease);
}

.smart-image__img.is-visible {
  opacity: 1;
  transform: scale(1);
}
</style>
