<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 20 },
  strokeWidth: { type: [Number, String], default: 1.7 },
  title: { type: String, default: '' },
})

const paths = {
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.8-3.8"/>',
  cart: '<circle cx="9" cy="20" r="1.3"/><circle cx="18" cy="20" r="1.3"/><path d="M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h8.9a2 2 0 0 0 2-1.6L22 8H6"/>',
  user: '<circle cx="12" cy="8" r="3.6"/><path d="M4.5 20c.7-3.4 3.8-5.4 7.5-5.4s6.8 2 7.5 5.4"/>',
  heart: '<path d="M20.4 5.6a5.2 5.2 0 0 0-7.4 0L12 6.6l-1-1a5.2 5.2 0 0 0-7.4 7.4l8.4 8.4 8.4-8.4a5.2 5.2 0 0 0 0-7.4Z"/>',
  'chevron-down': '<path d="m6 9 6 6 6-6"/>',
  'chevron-right': '<path d="m9 6 6 6-6 6"/>',
  'chevron-left': '<path d="m15 6-6 6 6 6"/>',
  'arrow-right': '<path d="M4 12h15"/><path d="m13 6 6 6-6 6"/>',
  'arrow-up': '<path d="M12 20V5"/><path d="m6 11 6-6 6 6"/>',
  monitor: '<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>',
  phone: '<rect x="6.5" y="2.5" width="11" height="19" rx="2.4"/><path d="M11 18.5h2"/>',
  home: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1Z"/>',
  shirt: '<path d="M8 3 4 5.5 5 10l2.5-.8V21h9V9.2l2.5.8L20 5.5 16 3a4 4 0 0 1-8 0Z"/>',
  shoe: '<path d="M3 16.5c0-2 1-3.2 3-4l3-1.2 3.2 2.2c1.6 1 3 1.4 5 1.4 2.6 0 4.3 1 4.3 3.1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1Z"/><path d="M9 11V8.5A2.5 2.5 0 0 1 11.5 6"/>',
  sparkle: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18"/>',
  baby: '<circle cx="12" cy="9" r="4"/><path d="M5 21c.8-3.9 3.5-6 7-6s6.2 2.1 7 6"/><path d="M9.7 8.5h.01M14.3 8.5h.01"/>',
  food: '<path d="M6 3v8a3 3 0 0 0 6 0V3M9 11v10M17 3c-1.5 1.4-2 3.2-2 5.5 0 2 .8 3.2 2 3.5V21"/>',
  lamp: '<path d="M8 3h8l3 9H5Z"/><path d="M12 12v8M8 21h8"/>',
  car: '<path d="m5 16 1.5-5.5A2 2 0 0 1 8.4 9h7.2a2 2 0 0 1 1.9 1.5L19 16"/><rect x="3" y="15" width="18" height="4" rx="1.6"/><path d="M7 19v2M17 19v2M6.5 13h.01M17.5 13h.01"/>',
  shield: '<path d="m12 3 8 3v6c0 5-3.3 8-8 9-4.7-1-8-4-8-9V6Z"/><path d="m8.8 12.2 2.2 2.2 4.4-4.4"/>',
  truck: '<path d="M3 6h11v11H3zM14 10h4l3 3v4h-7z"/><circle cx="7" cy="18" r="1.8"/><circle cx="18" cy="18" r="1.8"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.4 6.4"/><path d="M20 4v7h-7"/>',
  headset: '<path d="M4 14v-2a8 8 0 0 1 16 0v2"/><rect x="2.5" y="13" width="4.5" height="6" rx="2"/><rect x="17" y="13" width="4.5" height="6" rx="2"/><path d="M20 19a4 4 0 0 1-4 3h-2"/>',
  'shield-check': '<path d="m12 3 8 3v6c0 5-3.3 8-8 9-4.7-1-8-4-8-9V6Z"/><path d="m9 12 2 2 4-4"/>',
  pause: '<rect x="7" y="5" width="3.2" height="14" rx="1"/><rect x="13.8" y="5" width="3.2" height="14" rx="1"/>',
  play: '<path d="M8 5.5v13l10-6.5Z"/>',
  close: '<path d="m6 6 12 12M18 6 6 18"/>',
  menu: '<path d="M3 6h18M3 12h18M3 18h18"/>',
  map: '<path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z"/><path d="M9 3v15M15 6v15"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.2 2"/>',
  gift: '<rect x="3" y="9" width="18" height="12" rx="2"/><path d="M3 13h18M12 9v12"/><path d="M12 9c-1.8 0-4.5-.7-4.5-3a2.2 2.2 0 0 1 4-1.4L12 6l.5-1.4a2.2 2.2 0 0 1 4 1.4C16.5 8.3 13.8 9 12 9Z"/>',
  ticket: '<path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4Z"/><path d="M12 6v12"/>',
  star: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2-5.5-2.9-5.5 2.9 1-6.2L3 9.6l6.2-.9Z"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.6"/><rect x="14" y="3" width="7" height="7" rx="1.6"/><rect x="3" y="14" width="7" height="7" rx="1.6"/><rect x="14" y="14" width="7" height="7" rx="1.6"/>',
  trend: '<path d="m3 17 6-6 4 4 8-8"/><path d="M15 7h6v6"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="2.8"/>',
  'eye-off': '<path d="M3 3l18 18"/><path d="M10.6 6.1A9.9 9.9 0 0 1 12 6c6 0 9.5 6 9.5 6a17.6 17.6 0 0 1-3.2 3.9"/><path d="M6.2 6.2C3.8 7.8 2.5 12 2.5 12S6 18 12 18a9.6 9.6 0 0 0 4.2-.9"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
}

const inner = computed(() => paths[props.name] || paths.sparkle)
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    :role="title ? 'img' : undefined"
    :aria-hidden="title ? undefined : 'true'"
    focusable="false"
  >
    <title v-if="title">{{ title }}</title>
    <g v-html="inner" />
  </svg>
</template>

<style scoped>
.app-icon {
  display: block;
  flex-shrink: 0;
}
</style>

