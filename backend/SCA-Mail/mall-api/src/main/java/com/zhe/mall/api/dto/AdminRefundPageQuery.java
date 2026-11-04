package com.zhe.mall.api.dto;

/**
 * 后台退款单分页查询入参（mall-admin → mall-pay）。
 *
 * <p>与订单分页同样的取舍：筛选条件多且大多可选，用 POST + body。
 * 时间区间为 {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss} 文本，
 * 由 mall-pay 解析；格式非法时报参数错误，不静默忽略。
 */
public record AdminRefundPageQuery(
        String refundNo,
        String orderNo,
        Long userId,
        Integer status,
        String createdAtStart,
        String createdAtEnd,
        Integer pageNo,
        Integer pageSize) {

    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    public int offset() {
        return (safePageNo() - 1) * safePageSize();
    }
}
