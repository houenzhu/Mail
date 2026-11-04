<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useCarousel } from '@/composables/useCarousel'

const props = defineProps({
  slides: { type: Array, required: true },
  interval: { type: Number, default: 5200 },
})

const {
  activeIndex,
  isPaused,
  goTo,
  next,
  prev,
  toggle,
  pause,
  resume,
  handleKeydown,
} = useCarousel({ length: () => props.slides.length, interval: props.interval })

const slideCount = computed(() => props.slides.length)
const currentSlide = computed(() => props.slides[activeIndex.value] || props.slides[0])
</script>

<template>
  <section
    class="hero-banner"
    aria-roledescription="carousel"
    aria-label="商城活动推荐"
    @keydown="handleKeydown"
    @mouseenter="pause"
    @mouseleave="resume"
    @focusin="pause"
    @focusout="resume"
  >
    <div class="hero-banner__viewport">
      <article
        v-for="(slide, index) in slides"
        :key="slide.id"
        class="hero-slide"
        :class="[`hero-slide--${slide.theme}`, { 'is-active': index === activeIndex }]"
        :aria-hidden="index !== activeIndex"
        :aria-roledescription="'slide'"
        :aria-label="`${index + 1} / ${slideCount}`"
      >
        <div class="hero-slide__content">
          <p class="hero-slide__eyebrow">{{ slide.eyebrow }}</p>
          <h2 class="hero-slide__title">{{ slide.title }}</h2>
          <p class="hero-slide__subtitle">{{ slide.subtitle }}</p>
          <RouterLink
            class="hero-slide__cta"
            :to="{ name: 'search', query: { q: slide.query } }"
            :tabindex="index === activeIndex ? 0 : -1"
          >
            {{ slide.cta }}
            <AppIcon name="arrow-right" :size="16" />
          </RouterLink>
          <ul class="hero-slide__stats">
            <li v-for="stat in slide.stats" :key="stat.label">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </li>
          </ul>
        </div>
        <div class="hero-slide__visual">
          <SmartImage
            :src="slide.image"
            :alt="slide.imageAlt"
            :ratio="'16 / 11'"
            eager
            tone="rgba(255,255,255,0.4)"
          />
        </div>
      </article>
    </div>

    <div class="hero-banner__controls">
      <div class="hero-banner__dots" role="tablist" aria-label="切换活动">
        <button
          v-for="(slide, index) in slides"
          :key="`dot-${slide.id}`"
          class="hero-banner__dot"
          :class="{ 'is-active': index === activeIndex }"
          type="button"
          role="tab"
          :aria-selected="index === activeIndex"
          :aria-label="`查看第 ${index + 1} 张：${slide.title}`"
          @click="goTo(index)"
        />
      </div>
      <div class="hero-banner__arrows">
        <button class="hero-banner__arrow" type="button" aria-label="上一张" @click="prev">
          <AppIcon name="chevron-left" :size="16" />
        </button>
        <button
          class="hero-banner__arrow hero-banner__arrow--pause"
          type="button"
          :aria-label="isPaused ? '继续自动播放' : '暂停自动播放'"
          @click="toggle"
        >
          <AppIcon :name="isPaused ? 'play' : 'pause'" :size="14" />
        </button>
        <button class="hero-banner__arrow" type="button" aria-label="下一张" @click="next">
          <AppIcon name="chevron-right" :size="16" />
        </button>
      </div>
    </div>

    <p class="visually-hidden" aria-live="polite">
      当前展示：{{ currentSlide?.title }}
    </p>
  </section>
</template>

<style scoped>
.hero-banner {
  position: relative;
  height: 100%;
  min-height: 380px;
  border-radius: var(--mall-radius);
  overflow: hidden;
  box-shadow: var(--mall-shadow-sm);
}

.hero-banner__viewport {
  position: relative;
  height: 100%;
  min-height: inherit;
}

.hero-slide {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.08fr);
  align-items: center;
  gap: 12px;
  padding: 34px 36px 54px;
  opacity: 0;
  visibility: hidden;
  transform: translateX(16px);
  transition:
    opacity 0.5s var(--mall-ease),
    transform 0.5s var(--mall-ease),
    visibility 0.5s;
  overflow: hidden;
}

.hero-slide.is-active {
  opacity: 1;
  visibility: visible;
  transform: translateX(0);
}

.hero-slide--warm {
  background: linear-gradient(120deg, #fff2e7 0%, #ffe3d2 48%, #ffd6c0 100%);
  color: #6a3416;
}

.hero-slide--tech {
  background: linear-gradient(120deg, #eef2f7 0%, #e2e9f2 50%, #d3deec 100%);
  color: #22303f;
}

.hero-slide--blush {
  background: linear-gradient(120deg, #fdf0ec 0%, #f9dfd8 52%, #f4cdc4 100%);
  color: #6b3228;
}

.hero-slide__content {
  position: relative;
  z-index: 2;
  max-width: 380px;
}

.hero-slide__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: inherit;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  backdrop-filter: blur(4px);
}

.hero-slide__title {
  margin-top: 14px;
  font-size: 34px;
  font-weight: 800;
  line-height: 1.22;
  letter-spacing: -0.015em;
}

.hero-slide__subtitle {
  margin-top: 12px;
  color: inherit;
  opacity: 0.72;
  font-size: 14px;
  line-height: 1.65;
}

.hero-slide__cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 42px;
  margin-top: 22px;
  padding: 0 22px;
  border-radius: 999px;
  background: #1f1f1f;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  transition:
    background-color 0.2s var(--mall-ease),
    transform 0.2s var(--mall-ease);
}

.hero-slide__cta:hover {
  background: var(--mall-brand);
  transform: translateY(-1px);
}

.hero-slide__stats {
  display: flex;
  gap: 24px;
  margin-top: 26px;
}

.hero-slide__stats li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-left: 12px;
  border-left: 2px solid rgba(31, 31, 31, 0.16);
}

.hero-slide__stats span {
  font-size: 11px;
  opacity: 0.6;
}

.hero-slide__stats strong {
  font-size: 13px;
  font-weight: 700;
}

.hero-slide__visual {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  align-items: center;
}

.hero-slide__visual :deep(.smart-image) {
  width: 100%;
  border-radius: 16px;
  box-shadow: 0 24px 50px rgba(42, 28, 18, 0.18);
}

.hero-slide__visual :deep(.smart-image__img) {
  transition:
    opacity 0.6s var(--mall-ease),
    transform 6s linear;
}

.hero-slide.is-active .hero-slide__visual :deep(.smart-image__img) {
  transform: scale(1.045);
}

.hero-banner__controls {
  position: absolute;
  left: 36px;
  right: 36px;
  bottom: 20px;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: none;
}

.hero-banner__dots,
.hero-banner__arrows {
  display: flex;
  align-items: center;
  gap: 8px;
  pointer-events: auto;
}

.hero-banner__dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: rgba(31, 31, 31, 0.24);
  transition:
    width 0.3s var(--mall-ease),
    background-color 0.3s var(--mall-ease);
}

.hero-banner__dot.is-active {
  width: 22px;
  background: var(--mall-brand);
}

.hero-banner__arrow {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.66);
  color: var(--mall-ink);
  backdrop-filter: blur(6px);
  transition: background-color 0.2s var(--mall-ease);
}

.hero-banner__arrow:hover {
  background: #fff;
}

.hero-banner__arrow--pause {
  width: 26px;
  height: 26px;
}

@media (max-width: 1080px) {
  .hero-slide {
    padding: 28px 26px 50px;
  }

  .hero-slide__title {
    font-size: 27px;
  }

  .hero-slide__subtitle {
    font-size: 13px;
  }

  .hero-slide__stats {
    margin-top: 18px;
    gap: 16px;
  }
}

@media (max-width: 760px) {
  .hero-banner {
    min-height: 340px;
  }

  .hero-slide {
    grid-template-columns: 1fr;
    padding: 22px 20px 52px;
    gap: 8px;
  }

  .hero-slide__content {
    position: relative;
    z-index: 2;
    padding-right: 96px;
  }

  .hero-slide__visual {
    position: absolute;
    inset: 0;
  }

  .hero-slide__visual :deep(.smart-image) {
    height: 100%;
    border-radius: 0;
    box-shadow: none;
    opacity: 0.4;
  }

  .hero-slide__visual :deep(.smart-image__img) {
    object-position: right center;
  }

  .hero-slide__title {
    margin-top: 10px;
    font-size: 23px;
  }

  .hero-slide__subtitle {
    max-width: 230px;
    font-size: 12px;
  }

  .hero-slide__cta {
    height: 36px;
    margin-top: 16px;
    padding: 0 16px;
    font-size: 13px;
  }

  .hero-slide__stats {
    display: none;
  }

  .hero-banner__controls {
    left: 20px;
    right: 20px;
    bottom: 16px;
  }
}
</style>
