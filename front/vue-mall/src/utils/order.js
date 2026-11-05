/** 订单 / 支付展示口径：状态、倒计时、时间格式化集中在这里，避免各页面各写一套。 */

export const ORDER_STATUS = {
  CREATED: 10,
  PAID: 20,
  SHIPPED: 30,
  COMPLETED: 40,
  CANCELLED: 50,
  REFUNDING: 60,
  REFUNDED: 70,
}

export const ORDER_TABS = [
  { label: '全部', status: undefined },
  { label: '待支付', status: ORDER_STATUS.CREATED },
  { label: '待发货', status: ORDER_STATUS.PAID },
  { label: '待收货', status: ORDER_STATUS.SHIPPED },
  { label: '已完成', status: ORDER_STATUS.COMPLETED },
]

const STATUS_TONE = {
  [ORDER_STATUS.CREATED]: 'warn',
  [ORDER_STATUS.PAID]: 'brand',
  [ORDER_STATUS.SHIPPED]: 'brand',
  [ORDER_STATUS.COMPLETED]: 'muted',
  [ORDER_STATUS.CANCELLED]: 'muted',
  [ORDER_STATUS.REFUNDING]: 'warn',
  [ORDER_STATUS.REFUNDED]: 'muted',
}

export function statusTone(status) {
  return STATUS_TONE[Number(status)] || 'muted'
}

/** 业务动作可用性：与后端状态机保持一致，避免前端给出必然失败的按钮。 */
export function canPay(order) {
  return Number(order?.status) === ORDER_STATUS.CREATED
}

export function canCancel(order) {
  return Number(order?.status) === ORDER_STATUS.CREATED
}

export function canConfirm(order) {
  return Number(order?.status) === ORDER_STATUS.SHIPPED
}

export function isPaid(order) {
  return Number(order?.status) >= ORDER_STATUS.PAID && Number(order?.status) !== ORDER_STATUS.CANCELLED
}

/** 支付倒计时：返回毫秒差，<= 0 表示已超时（最终以后端扫描关单为准）。 */
export function remainingMs(expireAt, now = Date.now()) {
  if (!expireAt) return 0
  const target = new Date(expireAt).getTime()
  if (Number.isNaN(target)) return 0
  return Math.max(0, target - now)
}

export function formatCountdown(ms) {
  const total = Math.floor(Math.max(0, ms) / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export function shortOrderNo(orderNo) {
  const value = String(orderNo || '')
  return value.length > 12 ? `${value.slice(0, 6)}…${value.slice(-4)}` : value
}

/** 规格快照是 JSON 字符串；解析失败时原样展示，避免详情页因为脏数据整体崩掉。 */
export function formatSkuSpec(spec) {
  if (!spec) return ''
  try {
    const parsed = typeof spec === 'string' ? JSON.parse(spec) : spec
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed).map(([key, value]) => `${key}：${value}`).join(' / ')
    }
  } catch {
    // 非 JSON 的历史数据直接展示原文
  }
  return String(spec)
}
