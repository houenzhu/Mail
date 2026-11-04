<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import MasonryGrid from '@/components/common/MasonryGrid.vue'
import ProductCard from '@/components/product/ProductCard.vue'

const props = defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  degraded: { type: Boolean, default: false },
  curated: { type: Boolean, default: false },
})

const emit = defineEmits(['retry'])

const notice = computed(() => {
  if (props.degraded) return '演示商品 · 接口暂不可用'
  if (props.curated) return '部分为平台精选'
  return ''
})
</script>

<template>
  <section class="recommend" aria-labelledby="recommend-title">
    <div class="recommend__head">
      <div class="recommend__head-text">
        <span class="recommend__eyebrow">
          <AppIcon name="heart" :size="14" />
          FOR YOU
        </span>
        <h2 id="recommend-title" class="recommend__title">猜你喜欢</h2>
        <p class="recommend__subtitle">根据浏览与偏好，为你挑选</p>
      </div>
      <div class="recommend__head-side">
        <span v-if="notice" class="recommend__degraded">{{ notice }}</span>
        <RouterLink class="recommend__more" :to="{ name: 'search', query: { q: '' } }">
          查看更多
          <AppIcon name="arrow-right" :size="14" />
        </RouterLink>
      </div>
    </div>

    <div v-if="loading" class="recommend__skeleton" aria-busy="true" aria-label="推荐商品加载中">
      <div v-for="index in 8" :key="`skeleton-${index}`" class="recommend__card-skeleton">
        <div class="recommend__card-skeleton-media skeleton" />
        <div class="recommend__card-skeleton-line skeleton" />
        <div class="recommend__card-skeleton-line recommend__card-skeleton-line--short skeleton" />
      </div>
    </div>

    <MasonryGrid
      v-else-if="products.length"
      :items="products"
      :featured-index="0"
      :featured-span="2"
      :gap="16"
      aria-label="猜你喜欢商品"
    >
      <template #default="{ item, index }">
        <ProductCard
          :product="item"
          :featured="index === 0"
          :eager="index < 2"
        />
      </template>
    </MasonryGrid>

    <div v-else class="recommend__empty">
      <p>暂时没有推荐商品</p>
      <BaseButton variant="soft" size="sm" @click="emit('retry')">重新加载</BaseButton>
    </div>
  </section>
</template>

<style scoped>
.recommend {
  margin-top: 44px;
}

.recommend__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.recommend__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--mall-brand);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.recommend__title {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.recommend__subtitle {
  margin-top: 4px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.recommend__head-side {
  display: flex;
  align-items: center;
  gap: 14px;
}

.recommend__degraded {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fff7e6;
  color: #b7791f;
  font-size: 11px;
}

.recommend__more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--mall-ink-2);
  font-size: 13px;
  font-weight: 500;
  transition: color 0.18s var(--mall-ease);
}

.recommend__more:hover {
  color: var(--mall-brand);
}

/* 加载骨架复用瀑布流的列宽节奏，避免加载完成时整块跳动 */
.recommend__skeleton {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: start;
  gap: 16px;
}

.recommend__card-skeleton {
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: #fff;
  padding-bottom: 16px;
}

.recommend__card-skeleton:nth-child(4n + 2) .recommend__card-skeleton-media,
.recommend__card-skeleton:nth-child(4n + 3) .recommend__card-skeleton-media {
  padding-top: 18%;
}

.recommend__card-skeleton-media {
  aspect-ratio: 1;
}

.recommend__card-skeleton-line {
  height: 12px;
  margin: 12px 14px 0;
  border-radius: 4px;
}

.recommend__card-skeleton-line--short {
  width: 52%;
}

.recommend__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
  border-radius: var(--mall-radius);
  background: #fff;
  color: var(--mall-ink-3);
}

@media (max-width: 1080px) {
  .recommend__skeleton {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .recommend {
    margin-top: 32px;
  }

  .recommend__skeleton {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .recommend__card-skeleton:nth-child(4n + 2) .recommend__card-skeleton-media,
  .recommend__card-skeleton:nth-child(4n + 3) .recommend__card-skeleton-media {
    padding-top: 0;
  }

  .recommend__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
    margin-bottom: 14px;
  }

  .recommend__title {
    font-size: 20px;
  }
}
</style>
