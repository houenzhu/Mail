<script setup>
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import FloatingToolbar from '@/components/layout/FloatingToolbar.vue'
import HomeCategoryNav from '@/components/home/HomeCategoryNav.vue'
import HeroBanner from '@/components/home/HeroBanner.vue'
import QuickActivities from '@/components/home/QuickActivities.vue'
import HomeFeatureStrip from '@/components/home/HomeFeatureStrip.vue'
import HomeRecommend from '@/components/home/HomeRecommend.vue'
import HomeCategoryShowcase from '@/components/home/HomeCategoryShowcase.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useHomeContent } from '@/composables/useHomeContent'
import { heroSlides } from '@/data/homeContent'

const { products, isLoading, degraded, curated, load } = useHomeContent({ pageSize: 12 })
</script>

<template>
  <div class="home-page">
    <a class="skip-link" href="#main-content">跳到主要内容</a>

    <SiteHeader />

    <main id="main-content" class="home-main">
      <div class="mall-container">
        <section class="home-hero" aria-label="首页首屏">
          <div class="home-hero__nav">
            <HomeCategoryNav />
          </div>

          <div class="home-hero__stage">
            <HeroBanner :slides="heroSlides" />
          </div>

          <div class="home-hero__quick">
            <QuickActivities />
          </div>
        </section>

        <HomeFeatureStrip />
      </div>

      <div class="mall-container">
        <section class="home-offers" aria-label="活动专区">
          <RouterLink
            class="home-offer"
            :to="{ name: 'feature', params: { feature: 'member' } }"
          >
            <span class="home-offer__tag">会员日</span>
            <h2>每月 8 日 · 双倍积分</h2>
            <p>积分可抵现，会员专享好礼</p>
            <span class="home-offer__cta">
              了解会员权益
              <AppIcon name="arrow-right" :size="13" />
            </span>
          </RouterLink>
          <RouterLink
            class="home-offer home-offer--dark"
            :to="{ name: 'search', query: { q: '新品' } }"
          >
            <span class="home-offer__tag">新品尝鲜</span>
            <h2>本周上新 128 件</h2>
            <p>每日 10:00 更新，先到先得</p>
            <span class="home-offer__cta">
              去看看
              <AppIcon name="arrow-right" :size="13" />
            </span>
          </RouterLink>
          <RouterLink
            class="home-offer home-offer--image"
            :to="{ name: 'search', query: { q: '面霜' } }"
          >
            <span class="home-offer__tag">秋日限定</span>
            <h2>氛围感妆容指南</h2>
            <p>温柔低饱和，通勤也好看</p>
            <span class="home-offer__cta">
              挑选好物
              <AppIcon name="arrow-right" :size="13" />
            </span>
          </RouterLink>
        </section>
      </div>

      <div class="mall-container">
        <HomeRecommend
          :products="products"
          :loading="isLoading"
          :degraded="degraded"
          :curated="curated"
          @retry="load"
        />
      </div>

      <div class="mall-container">
        <HomeCategoryShowcase />
      </div>
    </main>

    <SiteFooter />
    <FloatingToolbar />
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.home-main {
  flex: 1;
  padding-bottom: 8px;
}

.home-hero {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 236px;
  gap: 16px;
  padding-top: 16px;
}

.home-hero__nav,
.home-hero__quick {
  min-height: 0;
}

.home-hero__stage {
  min-height: 380px;
}

.home-offers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.home-offer {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 108px;
  padding: 16px 20px;
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: linear-gradient(115deg, #fff3ea, #ffe6d8);
  transition:
    transform 0.24s var(--mall-ease),
    box-shadow 0.24s var(--mall-ease);
}

.home-offer:hover {
  transform: translateY(-3px);
  box-shadow: var(--mall-shadow);
}

.home-offer--dark {
  background: linear-gradient(115deg, #2c2c2c, #141414);
  color: #fff;
}

.home-offer--image {
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.97) 46%, rgba(255, 255, 255, 0.08) 100%),
    url('/images/products/lipstick.jpg') right center / cover no-repeat;
}

.home-offer__tag {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 80, 0, 0.12);
  color: var(--mall-brand);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.home-offer--dark .home-offer__tag {
  background: rgba(255, 255, 255, 0.16);
  color: #ffd7c2;
}

.home-offer h2 {
  margin-top: 2px;
  font-size: 16px;
  font-weight: 700;
}

.home-offer p {
  color: var(--mall-ink-3);
  font-size: 11px;
}

.home-offer--dark p {
  color: rgba(255, 255, 255, 0.64);
}

.home-offer__cta {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: auto;
  padding-top: 6px;
  color: var(--mall-ink);
  font-size: 12px;
  font-weight: 600;
}

.home-offer--dark .home-offer__cta {
  color: #fff;
}

@media (max-width: 1080px) {
  .home-hero {
    grid-template-columns: 220px minmax(0, 1fr) 210px;
    gap: 12px;
  }

  .home-hero__stage {
    min-height: 340px;
  }
}

@media (max-width: 960px) {
  .home-hero {
    grid-template-columns: minmax(0, 1fr) 220px;
    grid-template-areas:
      'stage quick'
      'nav nav';
    gap: 12px;
  }

  .home-hero__nav {
    grid-area: nav;
  }

  .home-hero__stage {
    grid-area: stage;
  }

  .home-hero__quick {
    grid-area: quick;
  }

  .home-hero__nav :deep(.category-nav__list) {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 6px;
  }

  .home-hero__nav :deep(.category-nav__footer) {
    display: none;
  }

  .home-hero__nav :deep(.category-nav) {
    padding: 12px;
  }

  .home-hero__nav :deep(.category-nav__item) {
    flex: 0 0 auto;
  }

  .home-hero__nav :deep(.category-nav__link) {
    padding: 6px 10px;
    border: 1px solid var(--mall-line);
  }

  .home-hero__nav :deep(.category-nav__chevron) {
    display: none;
  }
}

@media (max-width: 760px) {
  .home-hero {
    grid-template-columns: 1fr;
    grid-template-areas:
      'stage'
      'quick'
      'nav';
    padding-top: 10px;
  }

  .home-hero__stage {
    min-height: 0;
  }

  .home-offers {
    grid-template-columns: 1fr;
    gap: 10px;
    margin-top: 12px;
  }

  .home-offer {
    min-height: 92px;
  }
}
</style>
