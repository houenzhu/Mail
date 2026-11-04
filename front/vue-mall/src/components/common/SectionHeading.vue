<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  align: { type: String, default: 'left' },
})

const emits = defineEmits(['action'])
</script>

<template>
  <div class="section-heading" :class="`section-heading--${align}`">
    <div class="section-heading__text">
      <h2 class="section-heading__title">{{ title }}</h2>
      <p v-if="subtitle" class="section-heading__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.action" class="section-heading__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.section-heading--center {
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
}

.section-heading__title {
  font-size: 22px;
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.section-heading__subtitle {
  margin-top: 6px;
  color: var(--mall-ink-2);
  font-size: 13px;
}

.section-heading__action {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .section-heading {
    margin-bottom: 14px;
  }

  .section-heading__title {
    font-size: 18px;
  }

  .section-heading__subtitle {
    margin-top: 4px;
    font-size: 12px;
  }
}
</style>
