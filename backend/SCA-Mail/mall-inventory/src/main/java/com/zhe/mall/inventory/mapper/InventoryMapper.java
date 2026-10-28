package com.zhe.mall.inventory.mapper;

import com.zhe.mall.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface InventoryMapper {

    Inventory selectById(@Param("skuId") Long skuId);

    /**
     * 按 SKU 批量查询库存：管理端商品列表需要一次拿到多行库存，
     * 逐个查会产生 N+1（见计划文档 8.2 的取数方案）。
     */
    List<Inventory> selectByIds(@Param("skuIds") Collection<Long> skuIds);

    int deduct(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    int rollback(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    int init(@Param("skuId") Long skuId, @Param("qty") Integer qty, @Param("warnThreshold") Integer warnThreshold);
}
