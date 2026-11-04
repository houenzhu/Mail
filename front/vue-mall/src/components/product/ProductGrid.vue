<script setup>
import ProductCard from './ProductCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import BaseButton from '@/components/common/BaseButton.vue'

defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  skeletonCount: { type: Number, default: 8 },
  columns: { type: Number, default: 4 },
  emptyTitle: { type: String, default: '暂时没有找到相关商品' },
  emptyDescription: { type: String, default: '换个关键词，或看看为你推荐的其他好物。' },
})

const emit = defineEmits(['retry'])
</script>

<template>
  <div class="product-grid-wrap">
    <div
      v-if="loading"
      class="product-grid"
      :style="{ '--columns': columns }"
      aria-busy="true"
      aria-label="商品加载中"
    >
      <div v-for="index in skeletonCount" :key="`skeleton-${index}`" class="product-skeleton">
        <div class="product-skeleton__media skeleton" />
        <div class="product-skeleton__line skeleton" />
        <div class="product-skeleton__line product-skeleton__line--short skeleton" />
        <div class="product-skeleton__price skeleton" />
      </div>
    </div>

    <div
      v-else-if="products.length"
      class="product-grid"
      :style="{ '--columns': columns }"
    >
      <ProductCard
        v-for="(product, index) in products"
        :key="product.id"
        :product="product"
        :eager="index < columns"
      />
    </div>

    <EmptyState v-else :title="emptyTitle" :description="emptyDescription" icon="search">
      <BaseButton variant="soft" size="sm" @click="emit('retry')">重新加载</BaseButton>
    </EmptyState>
  </div>
</template>

<style scoped>
.product-grid {
  display: grid;
  grid-template-columns: repeat(var(--columns, 4), minmax(0, 1fr));
  gap: 16px;
}

.product-skeleton {
  overflow: hidden;
  border-radius: var(--mall-radius);
  background: #fff;
  padding-bottom: 16px;
}

.product-skeleton__media {
  aspect-ratio: 1;
}

.product-skeleton__line {
  height: 12px;
  margin: 12px 14px 0;
  border-radius: 4px;
}

.product-skeleton__line--short {
  width: 55%;
}

.product-skeleton__price {
  width: 38%;
  height: 18px;
  margin: 14px 14px 0;
  border-radius: 4px;
}

@media (max-width: 1080px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 780px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
}

@media (max-width: 380px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
