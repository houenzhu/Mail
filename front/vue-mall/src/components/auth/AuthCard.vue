<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  titleId: { type: String, required: true },
})
</script>

<template>
  <section class="auth-card" :aria-labelledby="titleId">
    <div class="auth-card__head">
      <h2 :id="titleId">{{ title }}</h2>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <slot />
  </section>
</template>

<style scoped>
.auth-card {
  padding: 32px 34px 28px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  box-shadow: 0 24px 60px rgba(31, 31, 31, 0.1);
}

.auth-card__head h2 {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.auth-card__head p {
  margin-top: 6px;
  color: var(--mall-ink-3);
  font-size: 13px;
}

@media (max-width: 560px) {
  .auth-card {
    padding: 22px 18px 20px;
    border-radius: var(--mall-radius);
  }
}
</style>