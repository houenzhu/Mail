<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import FloatingToolbar from '@/components/layout/FloatingToolbar.vue'
import ProductGrid from '@/components/product/ProductGrid.vue'
import { fetchProductPage } from '@/api/product'

const route = useRoute()
const router = useRouter()

const keyword = computed(() => String(route.query.q || '').trim())
const products = ref([])
const loading = ref(false)
const error = ref('')
const sortBy = ref(route.query.sort === 'sales' ? 'sales' : 'default')

const sortOptions = [
  { value: 'default', label: '综合' },
  { value: 'sales', label: '销量' },
]

function parseSales(text = '') {
  const value = parseFloat(text)
  if (!Number.isFinite(value)) return 0
  if (text.includes('万')) return value * 10000
  if (text.includes('千')) return value * 1000
  return value
}

async function search() {
  loading.value = true
  error.value = ''
  try {
    const page = await fetchProductPage({ keyword: keyword.value, pageNo: 1, pageSize: 24 })
    products.value = page.list
  } catch (e) {
    products.value = []
    error.value = e?.message || '搜索失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => keyword.value,
  () => search(),
  { immediate: true },
)

function changeSort(value) {
  sortBy.value = value
  router.replace({ query: { ...route.query, sort: value } })
}

const sortedProducts = computed(() => {
  if (sortBy.value !== 'sales') return products.value
  return [...products.value].sort((a, b) => parseSales(b.salesText) - parseSales(a.salesText))
})
</script>

<template>
  <div class="search-page">
    <SiteHeader />

    <main class="mall-container search-main">
      <nav class="search-breadcrumb" aria-label="面包屑">
        <RouterLink :to="{ name: 'home' }">首页</RouterLink>
        <AppIcon name="chevron-right" :size="12" />
        <span>搜索</span>
      </nav>

      <header class="search-head">
        <div>
          <h1 class="search-title">
            <template v-if="keyword">“{{ keyword }}” 的搜索结果</template>
            <template v-else>全部商品</template>
          </h1>
          <p class="search-meta">
            共找到 <strong>{{ sortedProducts.length }}</strong> 件好物
            <span v-if="error" class="search-error">· {{ error }}</span>
          </p>
        </div>

        <div class="search-sort" role="group" aria-label="排序方式">
          <button
            v-for="option in sortOptions"
            :key="option.value"
            class="search-sort__item"
            :class="{ 'is-active': sortBy === option.value }"
            type="button"
            :aria-pressed="sortBy === option.value"
            @click="changeSort(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </header>

      <ProductGrid
        :products="sortedProducts"
        :loading="loading"
        :skeleton-count="12"
        :empty-title="error ? '商品加载失败' : '没有找到相关商品'"
        :empty-description="error ? error : '试试更换关键词，或回到首页看看推荐。'"
        @retry="search"
      />

      <div v-if="!loading && !sortedProducts.length" class="search-back">
        <BaseButton variant="ghost" :to="{ name: 'home' }">返回首页</BaseButton>
      </div>
    </main>

    <SiteFooter />
    <FloatingToolbar />
  </div>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.search-main {
  flex: 1;
  padding-top: 18px;
}

.search-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.search-breadcrumb a:hover {
  color: var(--mall-brand);
}

.search-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin: 14px 0 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--mall-line);
}

.search-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.search-meta {
  margin-top: 6px;
  color: var(--mall-ink-3);
  font-size: 12px;
}

.search-meta strong {
  color: var(--mall-brand);
}

.search-error {
  color: #b7791f;
}

.search-sort {
  display: flex;
  padding: 3px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--mall-line);
}

.search-sort__item {
  height: 30px;
  padding: 0 16px;
  border-radius: 999px;
  color: var(--mall-ink-2);
  font-size: 13px;
  transition:
    background-color 0.18s var(--mall-ease),
    color 0.18s var(--mall-ease);
}

.search-sort__item.is-active {
  background: var(--mall-ink);
  color: #fff;
}

.search-back {
  display: flex;
  justify-content: center;
  padding: 20px 0 0;
}

@media (max-width: 640px) {
  .search-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .search-title {
    font-size: 18px;
  }
}
</style>
