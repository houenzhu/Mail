import { computed, onMounted, ref } from 'vue'
import { fetchProductPage } from '@/api/product'
import { demoProducts, heroSlides } from '@/data/homeContent'

/**
 * 首页内容聚合：
 * - 优先使用真实商品接口；
 * - 接口失败时整体降级到演示商品，并标记 degraded；
 * - 真实数据不足一屏时用演示精选补足（curated 标记）；
 * - 标记为售罄的占位商品不进推荐位，避免首页主推无效商品；
 * - loading / error / retry 状态集中在此，视图只负责呈现。
 */
export function useHomeContent({ pageSize = 12, minimum = 13 } = {}) {
  const products = ref([])
  const status = ref('loading')
  const errorMessage = ref('')
  const degraded = ref(false)
  const curated = ref(false)
  const total = ref(0)
  const lastUpdated = ref('')

  const isLoading = computed(() => status.value === 'loading')
  const isEmpty = computed(() => status.value === 'ready' && products.value.length === 0)

  const isSoldOutPlaceholder = (product) =>
    /库存为\s*0|售罄/.test(`${product.subtitle || ''}${product.title || ''}`)

  function buildFeed(realProducts) {
    const recommended = realProducts.filter((item) => !isSoldOutPlaceholder(item))
    const usedIds = new Set(recommended.map((item) => item.id))
    const fillers = demoProducts.filter((item) => !usedIds.has(item.id))
    const needed = Math.max(0, minimum - recommended.length)
    curated.value = needed > 0 && fillers.length > 0
    return [...recommended, ...fillers.slice(0, needed)]
  }

  async function load() {
    status.value = 'loading'
    errorMessage.value = ''
    try {
      const page = await fetchProductPage({ pageNo: 1, pageSize })
      total.value = page.total
      products.value = buildFeed(page.list)
      degraded.value = false
      status.value = 'ready'
      lastUpdated.value = new Date().toISOString()
    } catch (error) {
      products.value = demoProducts.slice(0, Math.max(minimum, pageSize))
      total.value = demoProducts.length
      degraded.value = true
      curated.value = false
      errorMessage.value = error?.message || '商品加载失败'
      status.value = 'ready'
    }
  }

  onMounted(load)

  return {
    products,
    status,
    isLoading,
    isEmpty,
    degraded,
    curated,
    errorMessage,
    total,
    lastUpdated,
    heroSlides,
    load,
  }
}
