package com.zhe.mall.pay.service;

import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.api.dto.RefundStage;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.common.util.SnowflakeIdGenerator;
import com.zhe.mall.pay.channel.ChannelRefundResult;
import com.zhe.mall.pay.channel.PayChannel;
import com.zhe.mall.pay.dto.RefundApplyRequest;
import com.zhe.mall.pay.dto.RefundVO;
import com.zhe.mall.pay.entity.Payment;
import com.zhe.mall.pay.entity.Refund;
import com.zhe.mall.pay.enums.PaymentStatus;
import com.zhe.mall.pay.enums.RefundStatus;
import com.zhe.mall.pay.mapper.PaymentMapper;
import com.zhe.mall.pay.mapper.RefundMapper;
import com.zhe.mall.pay.support.OrderClientWrapper;
import com.zhe.mall.pay.support.PayChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 退款流程（28.7 / ADR-024）。
 *
 * <p>本阶段只支持「已支付且未发货」的<b>全额</b>退款；部分退款、退货退款、审核流见 28.12。
 *
 * <p>幂等是双层结构：
 * <ol>
 *   <li>{@code refund.uk_active} 唯一索引拦住并发建单（同一支付单最多一条有效退款单）；</li>
 *   <li>{@code payment.status} CAS 拦住并发改状态（20 → 50 只有一个线程能成功）。</li>
 * </ol>
 * 刻意不加 {@code @Transactional}：流程里要调订单服务与渠道（跨进程），
 * 且每步都是单条 CAS，包事务只会把远程调用拖进事务边界。
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private static final String REFUND_NO_PREFIX = "R";

    /** 订单可退款状态：20 已支付 */
    private static final int ORDER_STATUS_PAID = 20;

    /** 订单已发货：本阶段不支持，需客服介入 */
    private static final int ORDER_STATUS_SHIPPED = 30;

    private static final String OPERATOR_USER = "user";

    private static final String OPERATOR_CS = "cs";

    private final RefundMapper refundMapper;

    private final PaymentMapper paymentMapper;

    private final OrderClientWrapper orderClient;

    private final PayChannelRegistry channelRegistry;

    public RefundService(RefundMapper refundMapper,
                         PaymentMapper paymentMapper,
                         OrderClientWrapper orderClient,
                         PayChannelRegistry channelRegistry) {
        this.refundMapper = refundMapper;
        this.paymentMapper = paymentMapper;
        this.orderClient = orderClient;
        this.channelRegistry = channelRegistry;
    }

    /**
     * 申请退款（28.7 六步）。
     *
     * <p>返回体带 {@code idempotentReplay}：重复申请不是错误，而是"返回已有退款单"。
     */
    public RefundVO apply(RefundApplyRequest request, Long userId) {
        String orderNo = request.orderNo();
        OrderPayView order = orderClient.getPayView(orderNo);
        requireOrderOwnership(order, userId);
        requireShippable(order);

        // 幂等优先：只要该订单已有"处理中 / 成功"的退款单，重复申请一律回放它。
        // 必须在订单状态校验之前——首次退款成功后订单已到 70，先判"必须 20"会把幂等回放变成 34002（实测踩到）
        Refund existing = refundMapper.selectActiveByOrderNo(orderNo);
        if (existing != null) {
            log.info("退款申请幂等命中: orderNo={}, refundNo={}", orderNo, existing.getRefundNo());
            return toVO(existing, true);
        }

        requireRefundableOrderStatus(order);

        Payment payment = paymentMapper.selectLatestPaidByOrderNo(orderNo);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND, "该订单没有支付成功的支付单: " + orderNo);
        }
        if (!Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS.code())) {
            throw new BusinessException(ResultCode.PAYMENT_STATUS_ILLEGAL,
                    "支付单状态不允许退款: " + PaymentStatus.of(payment.getStatus()).label());
        }

        CurrentUser currentUser = UserContext.get();
        boolean staff = currentUser != null && currentUser.isStaff();
        Refund refund = new Refund();
        String refundNo = REFUND_NO_PREFIX + SnowflakeIdGenerator.nextId();
        refund.setRefundNo(refundNo);
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setOrderNo(orderNo);
        refund.setUserId(order.userId());
        refund.setAmount(payment.getAmount());
        refund.setStatus(RefundStatus.REFUNDING.code());
        refund.setActiveKey(payment.getPaymentNo());
        refund.setReason(request.reason());
        refund.setOperator(staff ? OPERATOR_CS : OPERATOR_USER);
        refund.setOperatorId(userId);
        refund.setAppliedAt(LocalDateTime.now());
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            Refund replay = refundMapper.selectByActiveKey(payment.getPaymentNo());
            if (replay == null) {
                throw e;
            }
            log.info("退款申请并发幂等命中: orderNo={}, refundNo={}", orderNo, replay.getRefundNo());
            return toVO(replay, true);
        }
        log.info("退款单创建成功: refundNo={}, paymentNo={}, orderNo={}, amount={}",
                refundNo, payment.getPaymentNo(), orderNo, refund.getAmount());

        // 通知订单：20 → 60 退款中（失败只记 ERROR，订单侧按当前状态幂等）
        orderClient.notifyRefund(orderNo, new RefundCallbackRequest(refundNo, RefundStage.REFUNDING,
                refund.getAmount(), refund.getReason()));

        PayChannel channel = channelRegistry.byCode(payment.getChannel());
        ChannelRefundResult result = channel.refund(payment.getPaymentNo(), refundNo, refund.getAmount());
        if (result.success()) {
            refundMapper.markSuccess(refundNo, RefundStatus.REFUNDING.code(), RefundStatus.SUCCESS.code(),
                    result.channelRefundNo(), LocalDateTime.now());
            // 支付单 20 → 50 与订单 60 → 70 同批完成（28.5）
            paymentMapper.markRefunded(payment.getPaymentNo(), PaymentStatus.SUCCESS.code(),
                    PaymentStatus.REFUNDED.code());
            orderClient.notifyRefund(orderNo, new RefundCallbackRequest(refundNo, RefundStage.REFUNDED,
                    refund.getAmount(), "渠道退款成功"));
            log.info("退款成功: refundNo={}, paymentNo={}, channelRefundNo={}",
                    refundNo, payment.getPaymentNo(), result.channelRefundNo());
        } else {
            // 失败：active_key 置 NULL 允许重试，订单回到 20（驳回）
            refundMapper.markFailed(refundNo, RefundStatus.REFUNDING.code(), RefundStatus.FAILED.code(),
                    result.failReason(), LocalDateTime.now());
            orderClient.notifyRefund(orderNo, new RefundCallbackRequest(refundNo, RefundStage.REJECTED,
                    refund.getAmount(), result.failReason()));
            log.warn("退款失败（已允许重试）: refundNo={}, reason={}", refundNo, result.failReason());
        }
        return toVO(refundMapper.selectByRefundNo(refundNo), false);
    }

    /** 退款单查询：仅本人或客服可查（28.4）。 */
    public RefundVO get(String refundNo, Long userId) {
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "refundNo 不能为空");
        }
        Refund refund = refundMapper.selectByRefundNo(refundNo);
        if (refund == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND, "退款单不存在: " + refundNo);
        }
        if (userId == null || !userId.equals(refund.getUserId())) {
            CurrentUser currentUser = UserContext.get();
            if (currentUser == null || !currentUser.isStaff()) {
                log.warn("退款单越权访问被拒绝: refundNo={}, ownerUserId={}, requestUserId={}",
                        refundNo, refund.getUserId(), userId);
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该退款单");
            }
        }
        return toVO(refund, false);
    }

    private void requireOrderOwnership(OrderPayView order, Long userId) {
        if (userId != null && userId.equals(order.userId())) {
            return;
        }
        CurrentUser currentUser = UserContext.get();
        if (currentUser != null && currentUser.isStaff()) {
            return;
        }
        log.warn("退款越权申请被拒绝: orderNo={}, ownerUserId={}, requestUserId={}",
                order.orderNo(), order.userId(), userId);
        throw new BusinessException(ResultCode.FORBIDDEN, "无权对该订单申请退款");
    }

    /** 已发货订单本阶段不支持退款，提示客服介入（28.7 边界表 / 28.12）。 */
    private void requireShippable(OrderPayView order) {
        if (order.status() != null && order.status() == ORDER_STATUS_SHIPPED) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "已发货订单需客服介入（售后流程第四阶段上线）");
        }
    }

    /** 退款前置状态校验：只有"已支付未发货"可退（28.7 边界表）。 */
    private void requireRefundableOrderStatus(OrderPayView order) {
        if (order.status() == null || order.status() != ORDER_STATUS_PAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单状态不允许退款: " + order.statusLabel());
        }
    }

    private RefundVO toVO(Refund refund, boolean idempotentReplay) {
        return new RefundVO(refund.getRefundNo(), refund.getPaymentNo(), refund.getOrderNo(),
                refund.getAmount(), refund.getStatus(), RefundStatus.of(refund.getStatus()).label(),
                refund.getReason(), refund.getChannelRefundNo(), refund.getAppliedAt(),
                refund.getFinishedAt(), refund.getFailReason(), idempotentReplay);
    }
}
