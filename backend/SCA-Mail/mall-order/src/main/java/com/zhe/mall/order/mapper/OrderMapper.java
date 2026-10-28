package com.zhe.mall.order.mapper;

import com.zhe.mall.order.dto.OrderPageQuery;
import com.zhe.mall.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    Order selectByUserRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    List<Order> selectPage(@Param("query") OrderPageQuery query, @Param("offset") int offset, @Param("limit") int limit);

    long countPage(@Param("query") OrderPageQuery query);

    int insert(Order order);

    /**
     * 状态机兜底：带期望状态的条件更新（CAS），影响行数为 0 说明状态已被别的线程/实例改过。
     */
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("expectStatus") Integer expectStatus,
                     @Param("status") Integer status);

    int markPaid(@Param("orderNo") String orderNo,
                 @Param("expectStatus") Integer expectStatus,
                 @Param("status") Integer status,
                 @Param("payType") Integer payType,
                 @Param("paidAt") LocalDateTime paidAt);

    int markCancelled(@Param("orderNo") String orderNo,
                      @Param("expectStatus") Integer expectStatus,
                      @Param("status") Integer status,
                      @Param("cancelledAt") LocalDateTime cancelledAt,
                      @Param("cancelReason") String cancelReason);

    List<Order> selectTimeoutCandidates(@Param("limit") int limit);
}
