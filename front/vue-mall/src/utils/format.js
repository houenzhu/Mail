export function formatPrice(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  const [intPart, decimalPart] = num.toFixed(2).split('.')
  if (decimalPart === '00') return intPart
  return `${intPart}.${decimalPart}`
}

export function formatPriceRange(min, max) {
  const low = Number(min)
  const high = Number(max)
  if (!Number.isFinite(low)) return '价格待定'
  if (!Number.isFinite(high) || high === low) return `¥${formatPrice(low)}`
  return `¥${formatPrice(low)} - ${formatPrice(high)}`
}

export function formatSales(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '新品上架'
  if (num >= 10000) {
    const wan = num / 10000
    return `${wan >= 100 ? Math.round(wan) : wan.toFixed(1).replace(/\.0$/, '')}万+人付款`
  }
  if (num >= 1000) return `${(num / 1000).toFixed(1).replace(/\.0$/, '')}千+人付款`
  return `${num}人付款`
}

export function formatCount(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return ''
  if (num >= 10000) return `${Math.round(num / 100) / 100}万`
  return String(num)
}

export function truncate(text, max = 40) {
  if (typeof text !== 'string') return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}
