import { ref } from 'vue'

/**
 * 幂等号：一次业务流程只生成一次。
 *
 * - 进入结算页 / 支付页时创建；
 * - 网络超时或用户重复点击时复用同一个值，服务端据此返回首次结果；
 * - 流程成功或用户主动离开后调用 renew()，下一次是全新请求。
 *
 * 用 crypto.randomUUID（浏览器均支持）；不可用时退回时间戳 + 随机串，
 * 绝不使用纯时间戳 —— 同一毫秒双击会撞成同一个幂等号。
 */
function generate() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export function useRequestId(prefix = '') {
  const requestId = ref(prefix ? `${prefix}-${generate()}` : generate())

  function renew() {
    requestId.value = prefix ? `${prefix}-${generate()}` : generate()
    return requestId.value
  }

  return { requestId, renew }
}
