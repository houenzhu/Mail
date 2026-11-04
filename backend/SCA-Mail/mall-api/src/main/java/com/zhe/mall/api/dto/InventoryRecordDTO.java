package com.zhe.mall.api.dto;

import java.time.LocalDateTime;

/** 库存变动流水视图。SKU 名称由管理端 BFF 跨服务补齐。 */
public record InventoryRecordDTO(Long id, Long skuId, String orderNo, Integer type,
                                 Integer qty, Integer status, String remark,
                                 Long operatorId, String operatorName,
                                 LocalDateTime createdAt) {
}
