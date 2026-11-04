package com.zhe.mall.api.dto;

import java.util.List;

public record InventoryRecordPage(long total, int pageNo, int pageSize,
                                  List<InventoryRecordDTO> records) {
}
