import http from './request'

/**
 * 后台数据看板接口 —— 由 mall-admin(8111) 聚合，对外 `GET /api/admin/dashboard`。
 *
 * 单接口返回概览 + 趋势 + 库存统计：看板页一次请求即可渲染整页，
 * 避免打开时并发打三四个请求，每个都要过网关鉴权与 BFF。
 *
 * @param {object} [params]
 * @param {number} [params.days] 趋势天数，默认 7，服务端收敛到 1~90
 */
export function getDashboard({ days } = {}) {
  return http.get('/admin/dashboard', { params: days ? { days } : {} })
}
