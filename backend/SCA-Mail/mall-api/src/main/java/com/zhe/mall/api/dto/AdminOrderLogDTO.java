package com.zhe.mall.api.dto;

import java.time.LocalDateTime;

/** 后台订单状态流转时间线的一行。 */
public record AdminOrderLogDTO(Integer fromStatus, String fromStatusLabel,
                               Integer toStatus, String toStatusLabel,
                               String operator, String remark, LocalDateTime createdAt) {
}
