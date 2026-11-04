import http from './request'

/**
 * 后台退款管理接口 —— 由 mall-admin(8111) 提供，对外统一 `/api/admin/refund/**`。
 *
 * 退款申请仍走 C 端 `POST /api/pay/refund`（本阶段只做后台查询与审计）。
 */

/**
 * 退款单分页。
 * @param {object} params 支持 refundNo / orderNo / userId / status / createdAtStart / createdAtEnd / pageNo / pageSize
 * @returns {Promise<{total,pageNo,pageSize,list}>}
 */
export function pageRefunds(params) {
  return http.post('/admin/refund/page', params)
}

/** 退款单详情。 */
export function getRefundDetail(refundNo) {
  return http.get(`/admin/refund/${encodeURIComponent(refundNo)}`)
}
