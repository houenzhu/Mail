package com.zhe.mall.api.dto;

/**
 * 后台订单分页查询入参（mall-admin → mall-order）。
 *
 * <p>与 C 端 {@code OrderPageQuery} 的差异：C 端强制按当前登录用户过滤，
 * 后台**不按 userId 过滤**，而是把 userId 当作可选筛选条件（客服按买家查单）。
 *
 * <p>时间区间用 {@code createdAtStart/End}，由前端传 {@code yyyy-MM-dd HH:mm:ss} 文本，
 * 服务端按 LocalDateTime 解析；为空表示不限。分页字段沿用 pageNo/pageSize（上限 100）。
 */
public record AdminOrderPageQuery(
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
