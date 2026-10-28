/**
 * 统一格式化工具。口径对齐需求文档：
 *  - BR-20：所有业务时间以服务端 Asia/Shanghai 为准，客户端时间仅用于展示
 *  - 6.4：金额与时间格式统一、文案中文
 *  - BR-01：金额一律两位小数，禁止浮点运算
 */

/** 金额：¥1,234.50（两位小数） */
export function formatMoney(value) {
  const num = toNumber(value)
  if (num === null) return '-'
  return `¥${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/** 金额区间：¥11.50 ~ ¥20.00（无区间时只显示一个价） */
export function formatMoneyRange(min, max) {
  const lo = toNumber(min)
  const hi = toNumber(max)
  if (lo === null && hi === null) return '-'
  if (lo === null) return formatMoney(hi)
  if (hi === null || lo === hi) return formatMoney(lo)
  return `${formatMoney(lo)} ~ ${formatMoney(hi)}`
}

/** 数量：千分位 */
export function formatNumber(value) {
  const num = toNumber(value)
  if (num === null) return '-'
  return num.toLocaleString('zh-CN')
}

/** 时间：YYYY-MM-DD HH:mm:ss，兼容后端 LocalDateTime 字符串与 ISO 串 */
export function formatDateTime(value) {
  if (!value) return '-'
  const date = toDate(value)
  if (!date) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** 日期：YYYY-MM-DD */
export function formatDate(value) {
  const text = formatDateTime(value)
  return text === '-' ? text : text.slice(0, 10)
}

function toNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function toDate(value) {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'number') {
    const d = new Date(value)
    return Number.isNaN(d.getTime()) ? null : d
  }
  // 后端返回 "2026-09-19T11:22:33" 或 "2026-09-19 11:22:33"
  const normalized = String(value).replace(' ', 'T')
  const d = new Date(normalized)
  return Number.isNaN(d.getTime()) ? null : d
}
