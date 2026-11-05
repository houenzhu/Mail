package com.zhe.mall.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 结算预览（UC-CART-03）：勾选项合并为一次下单请求。
 *
 * <p>{@code items} 可直接转换为 {@code POST /api/order} 的 items；
 * {@code payAmount} 为实时价格汇总，最终金额仍以订单服务计算为准。
 */
public record CartCheckoutView(List<CartCheckoutItem> items, int totalQuantity,
                               BigDecimal payAmount) {
}
