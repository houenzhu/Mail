/**
 * UI 验收前置数据：用真实 C 端链路造一笔「已支付」订单。
 *
 * 为什么不用 SQL 直接改状态：验收要证明前端在**真实接口返回的数据**上能正常发货，
 * 直接写库会掩盖订单/支付/库存链路上的契约问题。
 *
 * 链路：买家登录 → 建单 → 发起支付 → 内部接口主动查单（Mock 渠道恒成功）
 *      → 订单 10 → 20。
 *
 * 用法：node scripts/seed-paid-order.mjs [gatewayBaseUrl]
 * 输出：订单号（stdout 最后一行）
 */
const GATEWAY = (process.argv[2] || 'http://127.0.0.1:10000').replace(/\/$/, '')
const INTERNAL_TOKEN = process.env.INTERNAL_TOKEN || 'dev-internal-token-please-change'

async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json().catch(() => null)
  if (!res.ok) throw new Error(`${path} HTTP ${res.status}: ${JSON.stringify(json)}`)
  if (!json || json.code !== 0) throw new Error(`${path} 业务失败: ${JSON.stringify(json)}`)
  return json.data
}

const login = await api('/api/auth/login', {
  method: 'POST',
  body: { username: 'buyer01', password: 'Buyer@123456' },
})
const token = login.accessToken

const requestId = `ui-${Date.now()}`
const order = await api('/api/order', {
  method: 'POST',
  token,
  body: {
    requestId,
    receiverName: 'UI 验收收货人',
    receiverPhone: '13800000000',
    receiverAddress: '上海市浦东新区测试路 1 号',
    remark: 'UI 发货验收用订单',
    items: [{ skuId: 1001, qty: 1 }],
  },
})

const pay = await api('/api/pay', {
  method: 'POST',
  token,
  body: { orderNo: order.orderNo, payType: 1, requestId: `pay-${requestId}` },
})

// 内部查单兜底：Mock 渠道查询恒返回成功，把支付单推到 20，进而订单 10 → 20。
// 必须直连 mall-pay（内部接口被网关拦截外部访问），并带上内部令牌。
const payBase = process.env.MALL_PAY_BASE || 'http://127.0.0.1:8106'
const syncRes = await fetch(`${payBase}/api/pay/internal/sync/${pay.paymentNo}`, {
  method: 'POST',
  headers: { 'X-Gateway-Token': INTERNAL_TOKEN },
})
const syncJson = await syncRes.json()
if (!syncRes.ok || syncJson.code !== 0) {
  throw new Error(`支付查单失败: HTTP ${syncRes.status} ${JSON.stringify(syncJson)}`)
}

const detail = await api(`/api/order/${order.orderNo}`, { token })
if (detail.order.status !== 20) {
  throw new Error(`订单未进入已支付，当前状态 ${detail.order.status}（${detail.order.statusLabel}）`)
}

console.log(`已支付订单: ${order.orderNo} / 支付单 ${pay.paymentNo}`)
console.log(order.orderNo)
