package com.zhe.mall.order.mapper;

import com.zhe.mall.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    int insertBatch(@Param("items") List<OrderItem> items);

    List<OrderItem> selectByOrderNo(@Param("orderNo") String orderNo);
}
