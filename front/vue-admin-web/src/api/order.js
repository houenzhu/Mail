import http from './request'

/**
 * 后台订单管理接口 —— 由 mall-admin(8111) 提供，对外统一 `/api/admin/order/**`。
 *
 * 契约与后端一一对应（需求文档第十八章 + M4 计划）：
 *  - 分页用 POST + body（筛选条件多且大多可选）；
 *  - 发货是唯一写接口（`POST /admin/order/{orderNo}/ship`，仅 OPERATOR/ADMIN）。
 */

/**
 * 后台订单分页。
 * @param {object} params 支持 orderNo / userId / status / createdAtStart / createdAtEnd / pageNo / pageSize
 * @returns {Promise<{total,pageNo,pageSize,list}>}
 */
export function pageOrders(params) {
  return http.post('/admin/order/page', params)
}

/** 后台订单详情：订单主体 + 明细 + 状态流转时间线 */
export function getOrderDetail(orderNo) {
  return http.get(`/admin/order/${encodeURIComponent(orderNo)}`)
}

/**
 * 发货：已支付 → 已发货，写入物流公司与单号。
 * 重复提交同一单号是幂等的；改单号会被后端拒绝（需走售后）。
 * @param {string} orderNo
 * @param {{shippingCompany: string, trackingNo: string}} payload
 */
export function shipOrder(orderNo, payload) {
  return http.post(`/admin/order/${encodeURIComponent(orderNo)}/ship`, payload)
}
