package com.zhe.mall.order.mapper;

import com.zhe.mall.order.dto.OrderItemStat;
import com.zhe.mall.order.entity.OrderItem;
import com.zhe.mall.api.dto.AdminProductSalesStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    int insertBatch(@Param("items") List<OrderItem> items);

    List<OrderItem> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 整页订单的明细批量查询（我的订单列表）。
     *
     * <p>一次 IN 查询替代「逐单查明细」的 N+1；调用方保证 orderNos 非空。
     */
    List<OrderItem> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    /** 批量取订单明细聚合（件数/数量），供后台列表展示。 */
    List<OrderItemStat> selectStatsByOrderNos(@Param("orderNos") List<String> orderNos);

    /**
     * 按商品聚合近 N 日销量（G-13，30 日销量）。
     *
     * <p>口径与看板 GMV 一致：只统计已支付及之后的状态（20/30/40/60），并按
     * {@code orders.paid_at} 归入时间窗；退款中(60)仍计入（退款尚未成功），
     * 已退款(70)不计入。这里刻意<b>不用</b> {@code order_item.created_at}：
     * 下单不等于成交，未支付订单不能算销量。
     */
    List<AdminProductSalesStat> selectProductSalesBetween(@Param("productIds") List<Long> productIds,
                                                          @Param("start") java.time.LocalDateTime start,
                                                          @Param("end") java.time.LocalDateTime end);
}

