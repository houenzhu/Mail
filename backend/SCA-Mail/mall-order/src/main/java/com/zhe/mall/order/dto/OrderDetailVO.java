package com.zhe.mall.order.dto;

import java.util.List;

public record OrderDetailVO(OrderDTO order, List<OrderItemDTO> items, List<OrderStatusLogDTO> logs) {
}
