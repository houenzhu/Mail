package com.zhe.mall.admin.dto;

import java.util.List;

public record AdminInventoryRecordPage(long total, int pageNo, int pageSize,
                                       List<AdminInventoryRecordRow> records) {
}
