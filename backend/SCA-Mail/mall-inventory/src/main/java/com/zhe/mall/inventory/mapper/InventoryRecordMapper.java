package com.zhe.mall.inventory.mapper;

import com.zhe.mall.inventory.entity.InventoryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryRecordMapper {

    int insert(InventoryRecord record);

    InventoryRecord selectByUniqueKey(@Param("orderNo") String orderNo,
                                      @Param("skuId") Long skuId,
                                      @Param("type") Integer type);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("remark") String remark);

    List<InventoryRecord> selectPage(@Param("skuId") Long skuId,
                                     @Param("type") Integer type,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    long countPage(@Param("skuId") Long skuId, @Param("type") Integer type);
}
