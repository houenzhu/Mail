package com.zhe.mall.product.dto;

import java.math.BigDecimal;

/**
 * C 端 SKU 视图：基础信息 + 实时可售库存。
 *
 * <p>{@code available} 为 {@code null} 表示库存服务暂时不可用、库存未知，
 * 前端应显示"库存查询失败"而不是"售罄"；加购时仍会由购物车服务重新校验实时库存。
 */
public record CProductSkuVO(Long id, Long productId, String skuCode, String name,
                            String specJson, BigDecimal price, BigDecimal marketPrice,
                            String image, Integer status, Integer available) {
}
