package com.zhe.mall.order.dto;

import java.util.List;

/**
 * 我的订单列表行：订单主体 + 明细。
 *
 * <p>列表页需要展示商品缩略信息，只返回订单主表会让前端再发 N 次详情请求；
 * 服务端按整页订单号一次批量查明细（无 N+1），拼在这里返回。
 */
public record OrderListVO(OrderDTO order, List<OrderItemDTO> items) {
}
