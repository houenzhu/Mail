package com.zhe.mall.admin.dto;

import java.time.LocalDateTime;

/** 管理端库存流水行，SKU 主数据由 BFF 跨服务补齐。 */
public record AdminInventoryRecordRow(Long id, Long skuId, String skuCode, String skuName,
                                      String orderNo, Integer type, Integer qty, Integer status,
                                      String remark, Long operatorId, String operatorName,
                                      LocalDateTime createdAt) {
}
