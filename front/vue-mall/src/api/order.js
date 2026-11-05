import { request } from './client'

/**
 * 订单接口（C 端）。
 *
 * 下单要求前端携带 requestId：进入结算页生成一次，重试 / 双击复用同一个值，
 * 订单成功或用户主动放弃后作废。服务端用 userId + requestId 做唯一索引兜底，
 * 因此重放不会重复下单。
 */

export function createOrder(payload, options = {}) {
  return request('/order', { method: 'POST', body: payload, ...options })
}

export function fetchOrders({ status, pageNo = 1, pageSize = 10 } = {}, options = {}) {
  return request('/order/page', { query: { status, pageNo, pageSize }, ...options })
}

export function fetchOrderDetail(orderNo, options = {}) {
  return request(`/order/${encodeURIComponent(orderNo)}`, options)
}

export function cancelOrder(orderNo, reason, options = {}) {
  return request(`/order/${encodeURIComponent(orderNo)}/cancel`, {
    method: 'POST',
    body: { reason },
    ...options,
  })
}

export function confirmOrder(orderNo, options = {}) {
  return request(`/order/${encodeURIComponent(orderNo)}/confirm`, { method: 'POST', ...options })
}
