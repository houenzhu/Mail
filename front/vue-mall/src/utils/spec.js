/** 解析后端 specJson 字符串，容忍 null、空串与非法 JSON。 */
export function parseSpecJson(raw) {
  if (typeof raw !== 'string' || !raw.trim()) return []
  try {
    const value = JSON.parse(raw)
    if (!value || typeof value !== 'object' || Array.isArray(value)) return []
    return Object.entries(value)
      .filter(([key, item]) => key && item !== null && item !== undefined && String(item).trim())
      .map(([key, item]) => ({ key, value: String(item) }))
  } catch {
    return []
  }
}

export function formatSpec(raw, fallback = '') {
  const entries = parseSpecJson(raw)
  if (!entries.length) return fallback
  return entries.map((entry) => `${entry.key}：${entry.value}`).join(' / ')
}

/** available 为 null 表示库存未知，不能当成售罄。 */
export function canBuySku(sku) {
  if (!sku || Number(sku.status) !== 1) return false
  return sku.available === null || sku.available === undefined || Number(sku.available) > 0
}

export function specOfSku(sku) {
  return parseSpecJson(sku?.specJson).reduce((result, entry) => {
    result[entry.key] = entry.value
    return result
  }, {})
}

/** 按出现顺序聚合成规格组，例如「颜色：白 / 黑」。 */
export function buildSpecGroups(skus = []) {
  const groups = new Map()
  skus.forEach((sku) => {
    parseSpecJson(sku?.specJson).forEach(({ key, value }) => {
      if (!groups.has(key)) groups.set(key, { key, label: key, options: [] })
      const group = groups.get(key)
      if (!group.options.some((option) => option.value === value)) {
        group.options.push({ value, skuIds: [] })
      }
      group.options.find((option) => option.value === value).skuIds.push(sku.id)
    })
  })
  return [...groups.values()]
}

export function specMatches(sku, selection = {}) {
  const spec = specOfSku(sku)
  return Object.entries(selection).every(([key, value]) => !value || spec[key] === value)
}

/** 某个规格值在其余已选条件下是否还存在可售 SKU。 */
export function specOptionPurchasable(skus = [], selection = {}, groupKey, value) {
  const next = { ...selection, [groupKey]: value }
  return skus.some((sku) => specMatches(sku, next) && canBuySku(sku))
}