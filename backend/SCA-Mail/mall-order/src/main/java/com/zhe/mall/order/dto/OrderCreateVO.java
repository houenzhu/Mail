package com.zhe.mall.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单响应。idempotentReplay = true 表示命中幂等，返回的是首次下单结果。
 */
public record OrderCreateVO(String orderNo, Integer status, String statusLabel,
                            BigDecimal payAmount, LocalDateTime expireAt, boolean idempotentReplay) {
}
