package com.zhe.mall.order.mapper;

import com.zhe.mall.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderStatusLogMapper {

    int insert(OrderStatusLog log);

    List<OrderStatusLog> selectByOrderNo(@Param("orderNo") String orderNo);
}
