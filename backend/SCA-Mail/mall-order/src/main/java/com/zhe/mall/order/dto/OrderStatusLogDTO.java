package com.zhe.mall.order.dto;

import java.time.LocalDateTime;

public record OrderStatusLogDTO(Integer fromStatus, Integer toStatus, String operator,
                                String remark, LocalDateTime createdAt) {
}
