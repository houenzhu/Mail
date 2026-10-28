package com.zhe.mall.pay.mapper;

import com.zhe.mall.pay.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentMapper {

    Payment selectByPaymentNo(@Param("paymentNo") String paymentNo);

    Payment selectByOrderRequest(@Param("orderNo") String orderNo, @Param("requestId") String requestId);

    /**
     * 订单最近一张"已支付"支付单（退款流程第 2 步，28.7）。
     *
     * <p>含已退款（50）：退款成功后支付单就变成 50，若只查 20，重复申请退款会退化成
     * "支付单不存在（35001）"，把幂等回放误报成错误。
     */
    Payment selectLatestPaidByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 对账数据源：支付时间落在业务日的支付单（{@code status in (20, 50)}）。
     *
     * <p>含已退款（50）：渠道流水里它仍是一笔成功交易，退款是另一条流水；只取 20 会把已退款批次
     * 误报成 CHANNEL_ONLY（28.8 口径修正）。
     */
    List<Payment> selectPaidBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 查询兜底候选：支付中且未过期（ADR-023）。 */
    List<String> selectQueryCandidates(@Param("limit") int limit);

    /** 超时关闭候选：待支付 / 支付中且已过期（28.8）。 */
    List<String> selectExpiredCandidates(@Param("limit") int limit);

    int insert(Payment payment);

    /**
     * 状态 CAS：带期望状态的条件更新（28.5）。
     *
     * <p>影响行数为 0 说明状态已被并发方改过，调用方必须重新读取后再判定，不能当成失败直接报错。
     */
    int updateStatus(@Param("paymentNo") String paymentNo,
                     @Param("expectStatus") Integer expectStatus,
                     @Param("status") Integer status);

    /** 支付成功：仅允许 {@code status in (0, 10)} → 20。 */
    int markPaid(@Param("paymentNo") String paymentNo,
                 @Param("status") Integer status,
                 @Param("channelTradeNo") String channelTradeNo,
                 @Param("paidAt") LocalDateTime paidAt);

    /** 支付失败：仅允许 {@code status in (0, 10)} → 30。 */
    int markFailed(@Param("paymentNo") String paymentNo,
                   @Param("status") Integer status,
                   @Param("failReason") String failReason);

    /** 退款成功：仅允许 {@code status = 20} → 50。 */
    int markRefunded(@Param("paymentNo") String paymentNo,
                     @Param("expectStatus") Integer expectStatus,
                     @Param("status") Integer status);

    /** 关闭同一订单下所有未关闭的支付单（0/10 → 40），保证"一订单一张未关闭支付单"（ADR-020）。 */
    int closeOpenByOrderNo(@Param("orderNo") String orderNo,
                           @Param("status") Integer status,
                           @Param("closedAt") LocalDateTime closedAt);

    /** 超时关闭：单张支付单的条件更新。 */
    int closeExpired(@Param("paymentNo") String paymentNo,
                     @Param("status") Integer status,
                     @Param("closedAt") LocalDateTime closedAt);
}
