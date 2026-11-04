<script setup>
import { computed } from 'vue'
import { useMasonryLayout, DEFAULT_MASONRY_BREAKPOINTS } from '@/composables/useMasonryLayout'

const props = defineProps({
  items: { type: Array, default: () => [] },
  breakpoints: { type: Array, default: () => DEFAULT_MASONRY_BREAKPOINTS },
  featuredIndex: { type: Number, default: -1 },
  featuredSpan: { type: Number, default: 2 },
  gap: { type: Number, default: 16 },
  mobileGap: { type: Number, default: 10 },
  ariaLabel: { type: String, default: '瀑布流内容' },
})

const { containerRef, columns, containerHeight, ready, setItemRef, styleFor } =
  useMasonryLayout({
    breakpoints: props.breakpoints,
    featuredIndex: props.featuredIndex,
    featuredSpan: props.featuredSpan,
    gapFallback: props.gap,
    items: () => props.items,
  })

const gapStyle = computed(() => `${props.gap}px`)
const mobileGapStyle = computed(() => `${props.mobileGap}px`)
</script>

<template>
  <div
    ref="containerRef"
    class="masonry"
    :class="{ 'is-ready': ready }"
    :style="{
      '--masonry-gap': gapStyle,
      '--masonry-gap-mobile': mobileGapStyle,
      '--masonry-columns': columns,
      height: containerHeight ? `${containerHeight}px` : undefined,
    }"
    role="list"
    :aria-label="ariaLabel"
  >
    <div
      v-for="(item, index) in items"
      :key="item.id ?? index"
      :ref="(el) => setItemRef(el, item.id ?? index, index)"
      class="masonry__item"
      :style="styleFor(item.id ?? index)"
      role="listitem"
    >
      <slot :item="item" :index="index" :featured="index === featuredIndex" />
    </div>
  </div>
</template>

<style scoped>
.masonry {
  position: relative;
  column-gap: var(--masonry-gap, 16px);
  transition: height 0.3s var(--mall-ease);
}

.masonry__item {
  position: absolute;
  top: 0;
  left: 0;
  will-change: transform;
}

.masonry:not(.is-ready) .masonry__item {
  opacity: 0;
}

.masonry.is-ready .masonry__item {
  opacity: 1;
  transition: opacity 0.3s var(--mall-ease);
}

@media (max-width: 640px) {
  .masonry {
    column-gap: var(--masonry-gap-mobile, 10px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .masonry,
  .masonry.is-ready .masonry__item {
    transition: none;
  }
}
</style>
