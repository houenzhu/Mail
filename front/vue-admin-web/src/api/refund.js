import http from './request'

/**
 * 后台退款管理接口 —— 由 mall-admin(8111) 提供，对外统一 `/api/admin/refund/**`。
 *
 * 退款申请仍走 C 端 `POST /api/pay/refund`；审核/驳回由本模块提供。
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

/**
 * 审核通过：待审核(0) → 退款中(10) → 渠道退款 → 成功(20)。
 *
 * 后端重复审批按幂等返回当前退款单，前端无需自己做去重。
 */
export function approveRefund(refundNo) {
  return http.post(`/admin/refund/${encodeURIComponent(refundNo)}/approve`)
}

/**
 * 审核驳回：待审核(0) → 失败(30)，原因落 fail_reason 并展示给买家。
 *
 * 驳回会释放幂等键，买家可以重新申请，因此不是终态封死。
 */
export function rejectRefund(refundNo, reason) {
  return http.post(`/admin/refund/${encodeURIComponent(refundNo)}/reject`, { reason })
}
