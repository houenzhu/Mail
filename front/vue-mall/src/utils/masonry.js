/** 按容器宽度决定列数，与视口无关，便于在侧栏等窄容器中复用 */
export function resolveMasonryColumns(width, breakpoints = []) {
  const sorted = [...breakpoints].sort((a, b) => a.minWidth - b.minWidth)
  let columns = sorted[0]?.columns || 1
  for (const item of sorted) {
    if (width >= item.minWidth) columns = item.columns
  }
  return Math.max(1, columns)
}

function placeInto(columnHeights, start, span, height, gap) {
  let top = 0
  for (let i = 0; i < span; i += 1) {
    top = Math.max(top, columnHeights[start + i])
  }
  const bottom = top + height + gap
  const next = [...columnHeights]
  for (let i = 0; i < span; i += 1) next[start + i] = bottom
  return { top, next }
}

function spanFor(index, totalColumns, featuredIndex, featuredSpan) {
  return index === featuredIndex ? Math.min(featuredSpan, totalColumns) : 1
}

/**
 * 前瞻式装箱：为当前卡片逐个尝试候选列，并用未来少量卡片模拟贪心落位，
 * 选“放完之后整体最高点最低”的那一列。这样既保持 DOM 顺序，
 * 又能把瀑布流尾部的落差压到一张卡以内。
 */
export function packMasonry({
  items = [],
  columns = 1,
  containerWidth = 0,
  gap = 16,
  featuredIndex = -1,
  featuredSpan = 2,
  lookahead = 8,
} = {}) {
  const totalColumns = Math.max(1, columns)
  const width = Math.max(0, containerWidth)
  const columnWidth = Math.max(0, (width - gap * (totalColumns - 1)) / totalColumns)
  const columnHeights = new Array(totalColumns).fill(0)
  const placements = []

  const heights = items.map((item) => Math.max(0, Number(item.height) || 0))

  function simulate(startColumns, fromIndex) {
    const sim = [...startColumns]
    const end = Math.min(items.length, fromIndex + lookahead)
    for (let i = fromIndex; i < end; i += 1) {
      const span = spanFor(i, totalColumns, featuredIndex, featuredSpan)
      let bestStart = 0
      let bestPeak = Infinity
      for (let c = 0; c <= totalColumns - span; c += 1) {
        let peak = 0
        for (let k = 0; k < span; k += 1) peak = Math.max(peak, sim[c + k])
        if (peak < bestPeak) {
          bestPeak = peak
          bestStart = c
        }
      }
      const placed = placeInto(sim, bestStart, span, heights[i], gap)
      for (let k = 0; k < sim.length; k += 1) sim[k] = placed.next[k]
    }
    return sim
  }

  items.forEach((item, index) => {
    const span = spanFor(index, totalColumns, featuredIndex, featuredSpan)
    const itemWidth = columnWidth * span + gap * (span - 1)
    const height = heights[index]
    const startCandidates = []
    for (let c = 0; c <= totalColumns - span; c += 1) startCandidates.push(c)

    let bestStart = startCandidates[0]
    let bestScore = Infinity
    for (const c of startCandidates) {
      const placed = placeInto(columnHeights, c, span, height, gap)
      const projected = simulate(placed.next, index + 1)
      const peak = Math.max(...projected)
      const trough = Math.min(...projected)
      // 优先放得低：减少尾列空洞；再看整体最高点与列间落差
      const score = peak + (peak - trough) * 0.35 - placed.next[c] * 0.001
      if (score < bestScore - 0.5) {
        bestScore = score
        bestStart = c
      }
    }

    const placed = placeInto(columnHeights, bestStart, span, height, gap)
    for (let k = 0; k < columnHeights.length; k += 1) columnHeights[k] = placed.next[k]

    placements.push({
      key: item.key,
      index,
      column: bestStart,
      span,
      left: bestStart * (columnWidth + gap),
      top: placed.top,
      width: itemWidth,
      height,
    })
  })

  const tallest = Math.max(0, ...columnHeights)
  return {
    placements,
    height: Math.max(0, tallest - gap),
    columnWidth,
    columnHeights: columnHeights.map((h) => Math.max(0, h - gap)),
  }
}
