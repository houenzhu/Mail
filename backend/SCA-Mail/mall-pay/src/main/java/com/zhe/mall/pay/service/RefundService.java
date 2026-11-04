package com.zhe.mall.pay.service;

import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.api.dto.RefundStage;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.auth.UserRole;
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
 * <p>本阶段只支持「已支付且未发货」的<b>全额</b>退款；部分退款与退货退款见 28.12。
 *
 * <p>两条入口：
 * <ul>
 *   <li>即时退款（C 端默认）：申请 → 10 退款中 → 渠道退款，保持 V1.11 契约；</li>
 *   <li>审核退款（{@code audit=true}）：申请只建 0 待审核单，由后台
 *       {@link #approve(String)} / {@link #reject(String, String)} 推进（12.24 / 28.7）。</li>
 * </ul>
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
        refund.setStatus(request.auditRequested()
                ? RefundStatus.PENDING_AUDIT.code()
                : RefundStatus.REFUNDING.code());
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
        log.info("退款单创建成功: refundNo={}, paymentNo={}, orderNo={}, amount={}, audit={}",
                refundNo, payment.getPaymentNo(), orderNo, refund.getAmount(), request.auditRequested());

        if (request.auditRequested()) {
            // 待审核阶段不调渠道、不改支付单与订单状态：资金动作必须等后台同意。
            // active_key 已占位，重复申请仍命中幂等，不会产生第二张待审单。
            return toVO(refund, false);
        }

        // 通知订单：20 → 60 退款中（失败只记 ERROR，订单侧按当前状态幂等）
        orderClient.notifyRefund(orderNo, new RefundCallbackRequest(refundNo, RefundStage.REFUNDING,
                refund.getAmount(), refund.getReason()));

        return executeChannelRefund(refund, payment, RefundStatus.REFUNDING.code());
    }

    /**
     * 后台审核同意：0 待审核 → 10 退款中 → 渠道退款（28.7 第 4~6 步）。
     *
     * <p>CAS 抢状态保证只有一个客服能把同一张单推进到退款中；抢不到且单子已被别人处理时，
     * 直接返回当前退款单（幂等），避免重复点"同意"触发两次渠道调用。
     */
    public RefundVO approve(String refundNo) {
        Refund refund = requireRefund(refundNo);
        if (Objects.equals(refund.getStatus(), RefundStatus.REFUNDING.code())
                || Objects.equals(refund.getStatus(), RefundStatus.SUCCESS.code())) {
            log.info("退款审核幂等命中: refundNo={}, status={}", refundNo, refund.getStatus());
            return toVO(refund, true);
        }
        if (!Objects.equals(refund.getStatus(), RefundStatus.PENDING_AUDIT.code())) {
            throw new BusinessException(ResultCode.REFUND_STATUS_ILLEGAL,
                    "退款单状态不允许审核: " + RefundStatus.of(refund.getStatus()).label());
        }
        CurrentUser operator = requireOperator();
        // 先验支付单再抢状态：抢到 10 之后才发现支付单不可退，会把单子永久卡在"退款中"。
        Payment payment = paymentMapper.selectLatestPaidByOrderNo(refund.getOrderNo());
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND,
                    "该订单没有支付成功的支付单: " + refund.getOrderNo());
        }
        if (!Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS.code())) {
            // 审核期间支付单可能已被别的退款单退掉；此时再调渠道就是重复退款。
            throw new BusinessException(ResultCode.PAYMENT_STATUS_ILLEGAL,
                    "支付单状态不允许退款: " + PaymentStatus.of(payment.getStatus()).label());
        }

        LocalDateTime auditedAt = LocalDateTime.now();
        int affected = refundMapper.markApproved(refundNo, RefundStatus.PENDING_AUDIT.code(),
                RefundStatus.REFUNDING.code(), operator.username(), operator.userId(), auditedAt);
        if (affected == 0) {
            Refund latest = refundMapper.selectByRefundNo(refundNo);
            if (latest != null && !Objects.equals(latest.getStatus(), RefundStatus.PENDING_AUDIT.code())) {
                log.info("退款审核并发幂等命中: refundNo={}, status={}", refundNo, latest.getStatus());
                return toVO(latest, true);
            }
            throw new BusinessException(ResultCode.REFUND_STATUS_ILLEGAL,
                    "退款审核失败：退款单状态已变更，请刷新后重试");
        }

        orderClient.notifyRefund(refund.getOrderNo(), new RefundCallbackRequest(refundNo,
                RefundStage.REFUNDING, refund.getAmount(), refund.getReason()));
        log.info("退款审核通过: refundNo={}, operator={}", refundNo, operator.username());
        return executeChannelRefund(refund, payment, RefundStatus.REFUNDING.code());
    }

    /**
     * 后台驳回：0 待审核 → 30 退款失败，原因落 {@code fail_reason}。
     *
     * <p>驳回不触碰支付单与订单——订单从未进入 60，买家可直接再申请。
     * {@code active_key} 置 NULL 允许重试；这与「渠道退款失败」的终态一致。
     */
    public RefundVO reject(String refundNo, String reason) {
        Refund refund = requireRefund(refundNo);
        if (Objects.equals(refund.getStatus(), RefundStatus.FAILED.code())) {
            log.info("退款驳回幂等命中: refundNo={}", refundNo);
            return toVO(refund, true);
        }
        if (!Objects.equals(refund.getStatus(), RefundStatus.PENDING_AUDIT.code())) {
            throw new BusinessException(ResultCode.REFUND_STATUS_ILLEGAL,
                    "退款单状态不允许驳回: " + RefundStatus.of(refund.getStatus()).label());
        }
        CurrentUser operator = requireOperator();
        String rejectReason = reason == null || reason.isBlank() ? "审核驳回" : reason.trim();
        LocalDateTime auditedAt = LocalDateTime.now();
        int affected = refundMapper.markRejected(refundNo, RefundStatus.PENDING_AUDIT.code(),
                RefundStatus.FAILED.code(), rejectReason, operator.username(), operator.userId(),
                auditedAt, auditedAt);
        if (affected == 0) {
            Refund latest = refundMapper.selectByRefundNo(refundNo);
            if (latest != null && Objects.equals(latest.getStatus(), RefundStatus.FAILED.code())) {
                log.info("退款驳回并发幂等命中: refundNo={}", refundNo);
                return toVO(latest, true);
            }
            throw new BusinessException(ResultCode.REFUND_STATUS_ILLEGAL,
                    "退款驳回失败：退款单状态已变更，请刷新后重试");
        }
        log.info("退款审核驳回: refundNo={}, operator={}, reason={}",
                refundNo, operator.username(), rejectReason);
        return toVO(refundMapper.selectByRefundNo(refundNo), false);
    }

    /** 执行渠道退款并联动支付单与订单（审核同意后复用；28.5 / 28.7）。 */
    private RefundVO executeChannelRefund(Refund refund, Payment payment, int expectStatus) {
        String refundNo = refund.getRefundNo();
        PayChannel channel = channelRegistry.byCode(payment.getChannel());
        ChannelRefundResult result = channel.refund(payment.getPaymentNo(), refundNo, refund.getAmount());
        if (result.success()) {
            refundMapper.markSuccess(refundNo, expectStatus, RefundStatus.SUCCESS.code(),
                    result.channelRefundNo(), LocalDateTime.now());
            // 支付单 20 → 50 与订单 60 → 70 同批完成（28.5）
            paymentMapper.markRefunded(payment.getPaymentNo(), PaymentStatus.SUCCESS.code(),
                    PaymentStatus.REFUNDED.code());
            orderClient.notifyRefund(refund.getOrderNo(), new RefundCallbackRequest(refundNo,
                    RefundStage.REFUNDED, refund.getAmount(), "渠道退款成功"));
            log.info("退款成功: refundNo={}, paymentNo={}, channelRefundNo={}",
                    refundNo, payment.getPaymentNo(), result.channelRefundNo());
        } else {
            // 失败：active_key 置 NULL 允许重试，订单回到 20（驳回）
            refundMapper.markFailed(refundNo, expectStatus, RefundStatus.FAILED.code(),
                    result.failReason(), LocalDateTime.now());
            orderClient.notifyRefund(refund.getOrderNo(), new RefundCallbackRequest(refundNo,
                    RefundStage.REJECTED, refund.getAmount(), result.failReason()));
            log.warn("退款失败（已允许重试）: refundNo={}, reason={}", refundNo, result.failReason());
        }
        return toVO(refundMapper.selectByRefundNo(refundNo), false);
    }

    private Refund requireRefund(String refundNo) {
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "refundNo 不能为空");
        }
        Refund refund = refundMapper.selectByRefundNo(refundNo.trim());
        if (refund == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND, "退款单不存在: " + refundNo);
        }
        return refund;
    }

    /**
     * 审核动作要求 OPERATOR（ADMIN 覆盖），客服只能看不能批钱。
     *
     * <p>控制器上的 {@code @RequireStaff(OPERATOR)} 是主边界；这里再判一次，
     * 是为了避免"以后有人新增一个审核入口却忘了加注解"就把资金审批漏给 CS。
     */
    private CurrentUser requireOperator() {
        CurrentUser operator = UserContext.get();
        if (operator == null || operator.userId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!operator.hasRole(UserRole.OPERATOR.name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无退款审核权限");
        }
        return operator;
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
