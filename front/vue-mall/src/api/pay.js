import { request } from './client'

/** 支付接口（C 端）。渠道参数与签名都由服务端下发，前端不持有渠道密钥。 */

export function createPayment({ orderNo, payType, requestId }, options = {}) {
  return request('/pay', {
    method: 'POST',
    body: { orderNo, payType, requestId },
    ...options,
  })
}

export function fetchPayment(paymentNo, options = {}) {
  return request(`/pay/${encodeURIComponent(paymentNo)}`, options)
}

/**
 * 模拟支付成功：仅 Mock 渠道可用。
 * 生产切换到真实渠道后服务端固定返回 35007，前端无需按环境分支。
 */
export function mockPay(paymentNo, options = {}) {
  return request(`/pay/mock/${encodeURIComponent(paymentNo)}/pay`, { method: 'POST', ...options })
}
