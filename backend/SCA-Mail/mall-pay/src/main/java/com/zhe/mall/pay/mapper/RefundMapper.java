package com.zhe.mall.pay.mapper;

import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.pay.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RefundMapper {

    Refund selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 按订单取有效退款单（待审核 / 处理中 / 成功）。
     *
     * <p>退款幂等必须<b>先于订单状态校验</b>：首次退款成功后订单已到 70 已退款，
     * 此时重复申请若先判"订单必须 20"就会报 34002，把幂等回放变成错误（28.7 边界表要求返回同一退款单）。
     * 含待审核（0）：审核中的单子同样占着 {@code active_key}，重复申请必须回放它而不是再建一张。
     */
    Refund selectActiveByOrderNo(@Param("orderNo") String orderNo);

    /** 按幂等键取有效退款单（待审核 / 处理中 / 成功）。 */
    Refund selectByActiveKey(@Param("activeKey") String activeKey);

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

    /**
     * 审核通过：0 → 10（CAS），同时落审核人与审核时间。
     *
     * <p>只改状态不改 {@code active_key}：退款中仍是有效单，重复申请继续命中幂等。
     */
    int markApproved(@Param("refundNo") String refundNo,
                     @Param("expectStatus") Integer expectStatus,
                     @Param("status") Integer status,
                     @Param("auditedBy") String auditedBy,
                     @Param("auditedById") Long auditedById,
                     @Param("auditedAt") java.time.LocalDateTime auditedAt);

    /**
     * 审核驳回：0 → 30（CAS），写驳回原因、审核人、审核时间与完成时间，并把 {@code active_key} 置 NULL。
     *
     * <p>置 NULL 后买家可重新申请；订单从未进入退款中，因此无需回调订单。
     */
    int markRejected(@Param("refundNo") String refundNo,
                     @Param("expectStatus") Integer expectStatus,
                     @Param("status") Integer status,
                     @Param("failReason") String failReason,
                     @Param("auditedBy") String auditedBy,
                     @Param("auditedById") Long auditedById,
                     @Param("auditedAt") java.time.LocalDateTime auditedAt,
                     @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /* ---------------- 后台退款查询 ---------------- */

    List<Refund> selectAdminPage(@Param("query") AdminRefundPageQuery query,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countAdminPage(@Param("query") AdminRefundPageQuery query);

    /** 待处理退款单数：待审核(0) + 退款中(10)，两者都需要后台动作。 */
    long countPending();
}
