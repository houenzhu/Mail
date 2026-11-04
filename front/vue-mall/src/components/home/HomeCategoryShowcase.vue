<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import { showcaseSections } from '@/data/homeContent'

const sections = computed(() => showcaseSections)
</script>

<template>
  <section class="showcase" aria-labelledby="showcase-title">
    <div class="showcase__intro">
      <p class="showcase__eyebrow">
        <AppIcon name="sparkle" :size="14" />
        生活灵感
      </p>
      <h2 id="showcase-title" class="showcase__title">好物，藏在每一个日常里</h2>
      <p class="showcase__desc">
        从清晨的第一杯咖啡，到深夜的一盏灯。我们按生活场景挑选耐看、好用、经得起时间的东西。
      </p>
      <RouterLink class="showcase__link" :to="{ name: 'search', query: { q: '' } }">
        浏览全部好物
        <AppIcon name="arrow-right" :size="15" />
      </RouterLink>
    </div>

    <div class="showcase__grid">
      <RouterLink
        v-for="item in sections"
        :key="item.id"
        class="showcase-card"
        :class="`showcase-card--${item.tone}`"
        :to="{ name: 'search', query: { q: item.query } }"
      >
        <div class="showcase-card__media">
          <SmartImage
            :src="item.image"
            :alt="item.title"
            :ratio="'16 / 10'"
            tone="var(--tone-bg)"
          />
        </div>
        <div class="showcase-card__body">
          <h3>{{ item.title }}</h3>
          <p>{{ item.subtitle }}</p>
          <ul class="showcase-card__tags">
            <li v-for="tag in item.tags" :key="tag">{{ tag }}</li>
          </ul>
        </div>
        <span class="showcase-card__go" aria-hidden="true">
          <AppIcon name="arrow-right" :size="14" />
        </span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.showcase {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 28px;
  margin-top: 44px;
  padding: 30px;
  border-radius: var(--mall-radius-lg);
  background: #fff;
  box-shadow: var(--mall-shadow-sm);
}

.showcase__intro {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.showcase__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--mall-brand);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.showcase__title {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: -0.015em;
}

.showcase__desc {
  margin-top: 12px;
  color: var(--mall-ink-2);
  font-size: 13px;
  line-height: 1.75;
}

.showcase__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 20px;
  color: var(--mall-ink);
  font-size: 13px;
  font-weight: 600;
  transition: color 0.2s var(--mall-ease);
}

.showcase__link:hover {
  color: var(--mall-brand);
}

.showcase__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-content: start;
}

.showcase-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: var(--mall-surface-2);
  transition:
    transform 0.28s var(--mall-ease),
    box-shadow 0.28s var(--mall-ease);
}

.showcase-card--slate {
  --tone-bg: #e9eef3;
}

.showcase-card--sand {
  --tone-bg: #f3eee6;
}

.showcase-card--sage {
  --tone-bg: #edf1ea;
}

.showcase-card--rose {
  --tone-bg: #f8ece9;
}

.showcase-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--mall-shadow);
}

.showcase-card__body {
  padding: 14px 16px 16px;
}

.showcase-card__body h3 {
  font-size: 15px;
  font-weight: 700;
}

.showcase-card__body p {
  margin-top: 3px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.showcase-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.showcase-card__tags li {
  padding: 2px 8px;
  border-radius: 999px;
  background: #fff;
  color: var(--mall-ink-2);
  font-size: 11px;
}

.showcase-card__go {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.88);
  color: var(--mall-ink);
  opacity: 0;
  transform: translateX(-4px);
  transition:
    opacity 0.22s var(--mall-ease),
    transform 0.22s var(--mall-ease);
}

.showcase-card:hover .showcase-card__go {
  opacity: 1;
  transform: translateX(0);
}

@media (max-width: 980px) {
  .showcase {
    grid-template-columns: 1fr;
    gap: 20px;
    padding: 24px;
  }

  .showcase__intro {
    display: block;
  }

  .showcase__title {
    font-size: 22px;
  }

  .showcase__desc {
    max-width: 560px;
  }
}

@media (max-width: 620px) {
  .showcase {
    margin-top: 28px;
    padding: 18px;
    border-radius: var(--mall-radius);
  }

  .showcase__grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .showcase-card__body {
    padding: 12px 14px 14px;
  }
}
</style>



