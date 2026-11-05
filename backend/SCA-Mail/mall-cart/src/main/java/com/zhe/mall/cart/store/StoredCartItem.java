package com.zhe.mall.cart.store;

/** 购物车持久化行：只存用户选择，不存商品价格与库存等易变数据。 */
public record StoredCartItem(Long skuId, int quantity, boolean selected) {
}
