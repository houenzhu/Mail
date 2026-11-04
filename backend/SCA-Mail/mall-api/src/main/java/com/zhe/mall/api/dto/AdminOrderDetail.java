package com.zhe.mall.api.dto;

import java.util.List;

/** 后台订单详情：订单主体 + 明细 + 状态流转时间线。 */
public record AdminOrderDetail(AdminOrderRow order,
                               List<AdminOrderItemDTO> items,
                               List<AdminOrderLogDTO> logs) {
}
