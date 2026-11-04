<script setup>
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import { quickActivities } from '@/data/homeContent'
</script>

<template>
  <aside class="quick-activities" aria-label="精选活动">
    <RouterLink
      v-for="activity in quickActivities"
      :key="activity.id"
      class="quick-card"
      :class="`quick-card--${activity.theme}`"
      :to="{ name: 'search', query: { q: activity.query } }"
    >
      <div class="quick-card__text">
        <strong>{{ activity.title }}</strong>
        <span>{{ activity.subtitle }}</span>
        <em>
          立即逛逛
          <AppIcon name="arrow-right" :size="12" />
        </em>
      </div>
      <SmartImage
        class="quick-card__image"
        :src="activity.image"
        :alt="activity.title"
        ratio="1"
        tone="rgba(255,255,255,0.3)"
      />
    </RouterLink>
  </aside>
</template>

<style scoped>
.quick-activities {
  display: grid;
  grid-template-rows: repeat(4, minmax(0, 1fr));
  gap: 10px;
  height: 100%;
}

.quick-card {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 0;
  padding: 14px 14px 14px 18px;
  border-radius: var(--mall-radius);
  overflow: hidden;
  color: #fff;
  transition:
    transform 0.25s var(--mall-ease),
    box-shadow 0.25s var(--mall-ease);
}

.quick-card:hover {
  transform: translateX(-4px);
  box-shadow: var(--mall-shadow);
}

.quick-card--orange {
  background: linear-gradient(120deg, #ff7a2f, #ff5000);
}

.quick-card--pink {
  background: linear-gradient(120deg, #ff8fb8, #f0528c);
}

.quick-card--gold {
  background: linear-gradient(120deg, #f5b94b, #e89317);
}

.quick-card--blue {
  background: linear-gradient(120deg, #5aa9f5, #2c7fe0);
}

.quick-card__text {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.quick-card__text strong {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.quick-card__text span {
  color: rgba(255, 255, 255, 0.86);
  font-size: 12px;
}

.quick-card__text em {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.92);
  font-size: 11px;
  font-style: normal;
  opacity: 0;
  transform: translateX(-4px);
  transition:
    opacity 0.2s var(--mall-ease),
    transform 0.2s var(--mall-ease);
}

.quick-card:hover .quick-card__text em {
  opacity: 1;
  transform: translateX(0);
}

.quick-card__image {
  position: absolute;
  right: -14px;
  bottom: -16px;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  opacity: 0.94;
  transition: transform 0.4s var(--mall-ease);
}

.quick-card:hover .quick-card__image {
  transform: scale(1.08) rotate(-3deg);
}

@media (max-width: 1080px) {
  .quick-card {
    padding: 12px 12px 12px 14px;
  }

  .quick-card__text strong {
    font-size: 14px;
  }

  .quick-card__image {
    width: 78px;
    height: 78px;
  }
}

@media (max-width: 900px) {
  .quick-activities {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    grid-template-rows: auto;
  }

  .quick-card {
    min-height: 92px;
  }

  .quick-card:hover {
    transform: translateY(-3px);
  }
}

@media (max-width: 640px) {
  .quick-activities {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .quick-card__image {
    width: 68px;
    height: 68px;
  }
}
</style>
