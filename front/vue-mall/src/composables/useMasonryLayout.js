import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { packMasonry, resolveMasonryColumns } from '@/utils/masonry'

/** 默认列数断点：以容器宽度为准，避免依赖全局视口宽度 */
export const DEFAULT_MASONRY_BREAKPOINTS = [
  { minWidth: 0, columns: 2 },
  { minWidth: 641, columns: 3 },
  { minWidth: 1081, columns: 4 },
]

const RATIO_PATTERN = /^(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)$/

function toRatioValue(ratio) {
  if (typeof ratio === 'number' && ratio > 0) return ratio
  const match = String(ratio || '').match(RATIO_PATTERN)
  if (!match) return 1
  const value = Number(match[1]) / Number(match[2])
  return Number.isFinite(value) && value > 0 ? value : 1
}

/**
 * 瀑布流布局：
 * - 首帧按图片比例 + 文本高度估算高度，避免卡片全部挤在左上角；
 * - 图片与字体就绪后用真实高度校正，误差只影响列尾；
 * - 卡片绝对定位，DOM 顺序不变，Tab 与读屏顺序不被视觉重排打乱；
 * - 精选卡可跨两列，窄屏自动退回单列；
 * - 数据追加 / 列数变化时自动重新装箱，可复用于推荐流、搜索结果。
 */
export function useMasonryLayout({
  breakpoints = DEFAULT_MASONRY_BREAKPOINTS,
  gapFallback = 16,
  featuredIndex = -1,
  featuredSpan = 2,
  items = () => [],
} = {}) {
  const containerRef = ref(null)
  const columns = ref(1)
  const containerHeight = ref(0)
  const ready = ref(false)
  const layout = shallowRef(new Map())

  let containerObserver = null
  let itemObserver = null
  let frame = 0
  let lastWidth = 0
  const observed = new Map()
  const pendingItems = new Set()

  const getItems = typeof items === 'function' ? items : () => items

  function syncColumns() {
    const el = containerRef.value
    if (!el) return null
    const width = el.getBoundingClientRect().width
    if (width > 0) columns.value = resolveMasonryColumns(width, breakpoints)
    return el
  }

  function readGap(el) {
    const value = Number.parseFloat(getComputedStyle(el).columnGap)
    return Number.isFinite(value) ? value : gapFallback
  }

  /**
   * 估算高度：仅在卡片尚未完成测量时使用。
   * 数值与 ProductCard 实际排版对齐，首帧不会明显错位。
   */
  function estimateHeight(index, width, sourceItems) {
    const product = sourceItems?.[index]
    const isFeatured = index === featuredIndex
    if (isFeatured) {
      const imageHeight = width * 0.56
      const extra = width < 520 ? 170 : 96
      return Math.max(imageHeight, width < 520 ? imageHeight + extra : 232) + 20
    }
    const ratio = toRatioValue(product?.imageRatio)
    const imageHeight = width / ratio
    const body = width < 190 ? 104 : width < 240 ? 112 : 124
    return imageHeight + body
  }

  function measureAndPlace() {
    frame = 0
    const el = syncColumns()
    if (!el) return

    const nodes = [...el.querySelectorAll(':scope > [data-masonry-key]')]
    if (!nodes.length) {
      layout.value = new Map()
      containerHeight.value = 0
      ready.value = true
      return
    }

    const totalColumns = Math.max(1, columns.value)
    const gap = readGap(el)
    const contentWidth = el.getBoundingClientRect().width
    const columnWidth = Math.max(1, (contentWidth - gap * (totalColumns - 1)) / totalColumns)
    const sourceItems = getItems()

    nodes.forEach((node) => {
      const index = Number(node.dataset.masonryIndex ?? -1)
      const span = index === featuredIndex ? Math.min(featuredSpan, totalColumns) : 1
      node.style.width = `${columnWidth * span + gap * (span - 1)}px`
    })

    const measured = nodes.map((node, index) => {
      const key = node.dataset.masonryKey
      const itemIndex = Number(node.dataset.masonryIndex ?? index)
      const span = itemIndex === featuredIndex ? Math.min(featuredSpan, totalColumns) : 1
      const width = columnWidth * span + gap * (span - 1)
      const realHeight = node.getBoundingClientRect().height
      const measuredBefore = node.dataset.masonryMeasured === 'true'
      const height = measuredBefore && realHeight > 0
        ? realHeight
        : Math.max(realHeight, estimateHeight(itemIndex, width, sourceItems))
      return { key, index: itemIndex, height }
    })

    const packed = packMasonry({
      items: measured,
      columns: totalColumns,
      containerWidth: contentWidth,
      gap,
      featuredIndex,
      featuredSpan,
    })

    const next = new Map()
    packed.placements.forEach((placement) => {
      next.set(String(placement.key), placement)
    })

    containerHeight.value = packed.height
    layout.value = next
    ready.value = true
  }

  function scheduleLayout() {
    if (frame) cancelAnimationFrame(frame)
    frame = requestAnimationFrame(measureAndPlace)
  }

  function unobserveAll() {
    for (const [key, el] of observed) {
      itemObserver?.unobserve(el)
      observed.delete(key)
    }
  }

  function observeItem(el) {
    if (!el) return
    if (!itemObserver) {
      pendingItems.add(el)
      return
    }
    const key = el.dataset.masonryKey
    const current = observed.get(key)
    if (current === el) return
    if (current) itemObserver.unobserve(current)
    observed.set(key, el)
    itemObserver.observe(el)
    scheduleLayout()
  }

  function setItemRef(el, key, index = -1) {
    if (!el) return
    el.dataset.masonryKey = String(key)
    el.dataset.masonryIndex = String(index)
    observeItem(el)
  }

  function styleFor(key) {
    const item = layout.value.get(String(key))
    if (!item) return { visibility: 'hidden' }
    return {
      width: `${item.width}px`,
      transform: `translate3d(${item.left}px, ${item.top}px, 0)`,
    }
  }

  function prune() {
    for (const [key, el] of observed) {
      if (!el.isConnected) {
        itemObserver?.unobserve(el)
        observed.delete(key)
      }
    }
  }

  watch(
    () => propsKey(),
    () => {
      if (!itemObserver) return
      unobserveAll()
      pendingItems.clear()
      const el = containerRef.value
      if (el) {
        el.querySelectorAll(':scope > [data-masonry-key]').forEach((node) => {
          node.removeAttribute('data-masonry-measured')
          observeItem(node)
        })
      }
      scheduleLayout()
    },
  )

  function propsKey() {
    return getItems()
      ?.map((item) => item?.id ?? item?.key ?? '')
      .join('|')
  }

  onMounted(() => {
    syncColumns()

    itemObserver = new ResizeObserver((entries) => {
      let changed = false
      for (const entry of entries) {
        if (!entry.target.isConnected) continue
        if (entry.target.dataset.masonryMeasured !== 'true') {
          entry.target.dataset.masonryMeasured = 'true'
        }
        changed = true
      }
      if (changed) scheduleLayout()
    })
    containerObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect?.width || 0
      if (Math.abs(width - lastWidth) < 0.5) return
      lastWidth = width
      scheduleLayout()
    })

    if (containerRef.value) {
      lastWidth = containerRef.value.getBoundingClientRect().width
      containerObserver.observe(containerRef.value)
    }
    for (const el of pendingItems) observeItem(el)
    pendingItems.clear()
    scheduleLayout()

    document.fonts?.ready.then(() => scheduleLayout())
  })

  onBeforeUnmount(() => {
    if (frame) cancelAnimationFrame(frame)
    itemObserver?.disconnect()
    containerObserver?.disconnect()
    pendingItems.clear()
    observed.clear()
  })

  return {
    containerRef,
    columns,
    containerHeight,
    ready,
    layout,
    setItemRef,
    styleFor,
    relayout: () => {
      prune()
      scheduleLayout()
    },
  }
}
