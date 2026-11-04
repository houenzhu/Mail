package com.zhe.mall.api.dto;

/** 库存流水分页查询。 */
public record InventoryRecordPageQuery(Long skuId, Integer type, Integer pageNo, Integer pageSize) {

    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    public long offset() {
        return (long) (safePageNo() - 1) * safePageSize();
    }
}
