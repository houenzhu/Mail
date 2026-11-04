import http from './request'

/**
 * 后台订单管理接口 —— 由 mall-admin(8111) 提供，对外统一 `/api/admin/order/**`。
 *
 * 契约与后端一一对应（需求文档第十八章 + M4 计划）：
 *  - 分页用 POST + body（筛选条件多且大多可选）；
 *  - 只读接口，发货等写操作在后续切片加入。
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
