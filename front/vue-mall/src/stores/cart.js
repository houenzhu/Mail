import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as cartApi from '@/api/cart'

const EMPTY_VIEW = {
  items: [],
  totalQuantity: 0,
  selectedCount: 0,
  selectedAmount: 0,
  allSelected: false,
}

function toItem(raw = {}) {
  return {
    ...raw,
    price: Number(raw.price) || 0,
    marketPrice: Number(raw.marketPrice) || 0,
    quantity: Number(raw.quantity) || 0,
    selected: Boolean(raw.selected),
    invalid: Boolean(raw.invalid),
    available: raw.available === null || raw.available === undefined
      ? null
      : Number(raw.available),
  }
}

/** 买家购物车：所有写操作都以后端返回的 CartView 为准，页头角标与页面共享同一份状态。 */
export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const totalQuantity = ref(0)
  const selectedCount = ref(0)
  const selectedAmount = ref(0)
  const allSelected = ref(false)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const selecting = ref(false)
  const removingSkuId = ref(null)
  const checkingOut = ref(false)

  const hasItems = computed(() => items.value.length > 0)
  const validItems = computed(() => items.value.filter((item) => !item.invalid))
  const invalidItems = computed(() => items.value.filter((item) => item.invalid))

  let generation = 0
  let loadPromise = null
  let writeQueue = Promise.resolve()

  function apply(view = EMPTY_VIEW) {
    const data = view || EMPTY_VIEW
    items.value = Array.isArray(data.items) ? data.items.map(toItem) : []
    totalQuantity.value = Number(data.totalQuantity) || 0
    selectedCount.value = Number(data.selectedCount) || 0
    selectedAmount.value = Number(data.selectedAmount) || 0
    allSelected.value = Boolean(data.allSelected)
    loaded.value = true
    error.value = ''
  }

  function reset() {
    generation += 1
    loadPromise = null
    items.value = []
    totalQuantity.value = 0
    selectedCount.value = 0
    selectedAmount.value = 0
    allSelected.value = false
    loading.value = false
    loaded.value = false
    error.value = ''
    selecting.value = false
    removingSkuId.value = null
    checkingOut.value = false
  }

  async function load({ force = false } = {}) {
    if (!force && loaded.value) return items.value
    if (loadPromise) return loadPromise

    const current = generation
    loading.value = true
    error.value = ''
    loadPromise = (async () => {
      try {
        const view = await cartApi.fetchCart()
        if (current === generation) apply(view)
        return items.value
      } catch (cause) {
        if (current === generation) error.value = cause?.message || '购物车加载失败'
        throw cause
      } finally {
        if (current === generation) loading.value = false
        loadPromise = null
      }
    })()
    return loadPromise
  }

  async function add(skuId, quantity = 1) {
    const view = await cartApi.addToCart({ skuId, quantity })
    apply(view)
    return view
  }

  /** 串行写入，避免快速点击时后发请求先到，导致数量回跳。 */
  function enqueue(task) {
    const run = writeQueue.then(task, task)
    writeQueue = run.catch(() => {})
    return run
  }

  async function changeQuantity(skuId, quantity) {
    return enqueue(async () => {
      const view = await cartApi.updateCart([{ skuId, quantity }])
      apply(view)
      return view
    })
  }

  async function select(skuIds, selected) {
    selecting.value = true
    try {
      const result = await cartApi.selectCart({ skuIds, selected })
      apply(result?.cart || result)
      return result
    } finally {
      selecting.value = false
    }
  }

  async function toggleItem(skuId, selected) {
    return select([skuId], selected)
  }

  async function toggleAll(selected) {
    const skuIds = validItems.value.map((item) => item.skuId)
    if (!skuIds.length) return null
    return select(skuIds, selected)
  }

  async function remove(skuId) {
    removingSkuId.value = skuId
    try {
      const view = await cartApi.removeCartItem(skuId)
      apply(view)
      return view
    } finally {
      removingSkuId.value = null
    }
  }

  async function checkout() {
    checkingOut.value = true
    try {
      return await cartApi.checkoutCart()
    } finally {
      checkingOut.value = false
    }
  }

  return {
    items,
    totalQuantity,
    selectedCount,
    selectedAmount,
    allSelected,
    loading,
    loaded,
    error,
    selecting,
    removingSkuId,
    checkingOut,
    hasItems,
    validItems,
    invalidItems,
    load,
    add,
    changeQuantity,
    select,
    toggleItem,
    toggleAll,
    remove,
    checkout,
    reset,
  }
})