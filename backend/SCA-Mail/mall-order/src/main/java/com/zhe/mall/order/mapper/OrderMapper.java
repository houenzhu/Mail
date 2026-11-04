package com.zhe.mall.order.mapper;

import com.zhe.mall.api.dto.AdminOrderCountStat;
import com.zhe.mall.api.dto.AdminOrderDailyCount;
import com.zhe.mall.api.dto.AdminOrderDailyStat;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
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

    /** 发货：20 → 30，同一 CAS 内写入物流信息，避免"状态已变但物流没落库"。 */
    int markShipped(@Param("orderNo") String orderNo,
                    @Param("expectStatus") Integer expectStatus,
                    @Param("status") Integer status,
                    @Param("shippingCompany") String shippingCompany,
                    @Param("trackingNo") String trackingNo,
                    @Param("shippedAt") LocalDateTime shippedAt,
                    @Param("shippedBy") String shippedBy);

    List<Order> selectTimeoutCandidates(@Param("limit") int limit);
    /* ---------------- 后台订单查询 ---------------- */

    List<Order> selectAdminPage(@Param("query") AdminOrderPageQuery query,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    long countAdminPage(@Param("query") AdminOrderPageQuery query);

    /* ---------------- 后台看板聚合（UC-ADMIN-03） ---------------- */

    /**
     * 按日聚合 GMV 与支付成功订单数（按 paid_at 归日）。
     *
     * <p>只统计 paid_at 不为空且状态在「已支付及之后」的订单。
     * 退款中/已退款仍计入——GMV 是成交额，退款金额单独呈现，不做净额扣减。
     */
    List<AdminOrderDailyStat> selectDailyStats(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /** 按日聚合下单数（按 created_at 归日，含未支付；不含已逻辑删除）。 */
    List<AdminOrderDailyCount> selectDailyOrderCounts(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    /** 全量状态计数：一次 GROUP BY 拿到所有状态，避免按状态逐个 count。 */
    List<AdminOrderCountStat> selectStatusCounts();

    /** 指定时间窗内（按 created_at）的订单数。 */
    long countCreatedBetween(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);
}
