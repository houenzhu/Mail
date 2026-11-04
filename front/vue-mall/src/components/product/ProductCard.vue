<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import BaseTag from '@/components/common/BaseTag.vue'

const props = defineProps({
  product: { type: Object, required: true },
  layout: { type: String, default: 'grid' },
  eager: { type: Boolean, default: false },
  featured: { type: Boolean, default: false },
})

const toneByKey = {
  headphones: '#eeeeea',
  keyboard: '#eceef1',
  camera: '#e9ebee',
  apparel: '#f5f2ec',
  tshirt: '#f5f2ec',
  sneakers: '#f2ecec',
  bag: '#f0ece6',
  watch: '#efece7',
  chair: '#f3efe8',
  home: '#f3efe8',
  coffee: '#efeae4',
  beauty: '#f8eeec',
  skincare: '#f8eeec',
  lipstick: '#f9eceb',
  perfume: '#f4ede7',
  digital: '#eef1f4',
  default: '#f4f1ed',
}

const badgeText = computed(() => props.product.badge || props.product.tag || '')
const imageRatio = computed(() => {
  if (props.featured) return '1'
  return props.product.imageRatio || '1'
})

/** 服饰类主图人物居中偏上，避免横版裁掉脸 */
const imagePosition = computed(() => {
  const key = props.product.imageKey
  if (['tshirt', 'apparel', 'sneakers', 'bag'].includes(key)) return 'center 28%'
  return 'center'
})
</script>

<template>
  <RouterLink
    class="product-card"
    :class="[`product-card--${layout}`, { 'product-card--featured': featured }]"
    :to="product.detailPath"
    :aria-label="`${product.title}，${product.priceRange}`"
  >
    <div class="product-card__media">
      <SmartImage
        :src="product.image"
        :alt="product.title"
        :fallback-key="product.imageKey"
        :eager="eager"
        :tone="toneByKey[product.imageKey] || toneByKey.default"
        :ratio="imageRatio"
        :position="imagePosition"
      />
      <span v-if="badgeText" class="product-card__badge">
        <BaseTag tone="brand" size="sm" solid>{{ badgeText }}</BaseTag>
      </span>
      <span v-if="featured" class="product-card__featured-label">
        <AppIcon name="sparkle" :size="12" />
        编辑精选
      </span>
    </div>

    <div class="product-card__body">
      <h3 class="product-card__title">{{ product.title }}</h3>
      <p v-if="product.subtitle" class="product-card__subtitle">{{ product.subtitle }}</p>
      <div class="product-card__meta">
        <span class="product-card__price">{{ product.priceRange }}</span>
        <span class="product-card__sales">{{ product.salesText }}</span>
      </div>
      <span v-if="featured" class="product-card__cta">
        查看详情
        <AppIcon name="arrow-right" :size="14" />
      </span>
    </div>

    <span class="product-card__quick" aria-hidden="true">
      <AppIcon name="arrow-right" :size="15" />
    </span>
  </RouterLink>
</template>

<style scoped>
.product-card {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: #fff;
  border: 1px solid transparent;
  transition:
    transform 0.28s var(--mall-ease),
    box-shadow 0.28s var(--mall-ease),
    border-color 0.28s var(--mall-ease);
}

.product-card:hover {
  transform: translateY(-4px);
  border-color: rgba(255, 80, 0, 0.18);
  box-shadow: var(--mall-shadow);
}

.product-card__media {
  position: relative;
  background: var(--mall-surface-2);
}

.product-card__badge {
  position: absolute;
  top: 10px;
  left: 10px;
}

.product-card__featured-label {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(31, 31, 31, 0.78);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}

.product-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px 16px;
}

.product-card__title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--mall-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-card__subtitle {
  display: -webkit-box;
  overflow: hidden;
  color: var(--mall-ink-3);
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.product-card__meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 8px;
}

.product-card__price {
  color: var(--mall-brand);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.product-card__sales {
  flex-shrink: 0;
  color: var(--mall-ink-3);
  font-size: 11px;
}

.product-card__cta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: var(--mall-ink);
  font-size: 13px;
  font-weight: 600;
}

.product-card__quick {
  position: absolute;
  right: 12px;
  bottom: 14px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--mall-brand);
  color: #fff;
  opacity: 0;
  transform: translateX(-6px);
  transition:
    opacity 0.24s var(--mall-ease),
    transform 0.24s var(--mall-ease);
}

.product-card:hover .product-card__quick,
.product-card:focus-visible .product-card__quick {
  opacity: 1;
  transform: translateX(0);
}

.product-card--list {
  flex-direction: row;
  align-items: stretch;
}

.product-card--list .product-card__media {
  flex: 0 0 148px;
}

.product-card--list .product-card__body {
  padding: 16px 18px;
}

.product-card--list .product-card__title {
  font-size: 15px;
}

/* 瀑布流精选位：横跨两列，左图右文，不打断整条推荐流 */
.product-card--featured {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(0, 1fr);
  align-items: stretch;
}

.product-card--featured .product-card__media {
  height: 100%;
}

.product-card--featured .product-card__media :deep(.smart-image) {
  height: 100%;
  aspect-ratio: 1 !important;
}

.product-card--featured .product-card__body {
  justify-content: center;
  background: linear-gradient(120deg, #fffdfb, #fff8f4);
  gap: 8px;
  padding: 26px 28px;
}

.product-card--featured .product-card__title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  -webkit-line-clamp: 2;
}

.product-card--featured .product-card__subtitle {
  font-size: 13px;
  line-height: 1.6;
  -webkit-line-clamp: 2;
}

.product-card--featured .product-card__meta {
  margin-top: 6px;
  padding-top: 0;
}

.product-card--featured .product-card__price {
  font-size: 25px;
}

.product-card--featured .product-card__cta {
  margin-top: 8px;
}

@media (max-width: 640px) {
  .product-card__body {
    gap: 4px;
    padding: 10px 10px 12px;
  }

  .product-card__title {
    font-size: 13px;
  }

  .product-card__price {
    font-size: 16px;
  }

  .product-card__sales {
    display: none;
  }

  .product-card--featured {
    grid-template-columns: 1fr;
  }

  .product-card--featured .product-card__media :deep(.smart-image) {
    aspect-ratio: 16 / 10 !important;
  }

  .product-card--featured .product-card__body {
    padding: 12px 14px 14px;
  }

  .product-card--featured .product-card__title {
    font-size: 16px;
  }

  .product-card--featured .product-card__subtitle {
    font-size: 12px;
    -webkit-line-clamp: 1;
  }

  .product-card--featured .product-card__meta {
    padding-top: 2px;
  }

  .product-card--featured .product-card__price {
    font-size: 20px;
  }

  .product-card--featured .product-card__cta {
    display: none;
  }
}
</style>


