package com.zhe.mall.api.dto;

import java.util.List;

/**
 * 批量库存查询入参（管理端商品列表聚合用）。
 *
 * <p>说明：用 POST + body 而不是 GET + 重复 query 参数，是为了避免商品页 SKU 数量多时
 * URL 过长，也便于 Feign 直接序列化。
 */
public record InventoryBatchRequest(List<Long> skuIds) {
}
