package com.zhe.mall.pay.mapper;

import com.zhe.mall.pay.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {

    Refund selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 按订单取有效退款单（处理中 / 成功）。
     *
     * <p>退款幂等必须<b>先于订单状态校验</b>：首次退款成功后订单已到 70 已退款，
     * 此时重复申请若先判"订单必须 20"就会报 34002，把幂等回放变成错误（28.7 边界表要求返回同一退款单）。
     */
    Refund selectActiveByOrderNo(@Param("orderNo") String orderNo);

    /** 按幂等键取有效退款单（处理中 / 成功）。 */    Refund selectByActiveKey(@Param("activeKey") String activeKey);

    int insert(Refund refund);

    /** 退款成功：10 → 20，保留 active_key。 */
    int markSuccess(@Param("refundNo") String refundNo,
                    @Param("expectStatus") Integer expectStatus,
                    @Param("status") Integer status,
                    @Param("channelRefundNo") String channelRefundNo,
                    @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /**
     * 退款失败：10 → 30，同时把 {@code active_key} 置 NULL。
     *
     * <p>置 NULL 是"允许重试"的关键：MySQL 唯一索引允许多个 NULL，因此同一支付单可以再建一张退款单（ADR-024）。
     */
    int markFailed(@Param("refundNo") String refundNo,
                   @Param("expectStatus") Integer expectStatus,
                   @Param("status") Integer status,
                   @Param("failReason") String failReason,
                   @Param("finishedAt") java.time.LocalDateTime finishedAt);
}
