package com.zhe.mall.api.dto;

/**
 * 后台退款详情。
 *
 * <p>目前退款单本身已包含全部审计信息，没有额外的明细行；
 * 用独立记录是为了后续加"退款操作日志 / 渠道流水"时契约不变。
 */
public record AdminRefundDetail(AdminRefundRow refund) {
}
