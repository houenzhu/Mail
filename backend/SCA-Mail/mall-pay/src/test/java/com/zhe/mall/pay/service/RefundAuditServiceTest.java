package com.zhe.mall.pay.service;

import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.pay.channel.ChannelRefundResult;
import com.zhe.mall.pay.channel.PayChannel;
import com.zhe.mall.pay.dto.RefundApplyRequest;
import com.zhe.mall.pay.entity.Payment;
import com.zhe.mall.pay.entity.Refund;
import com.zhe.mall.pay.mapper.PaymentMapper;
import com.zhe.mall.pay.mapper.RefundMapper;
import com.zhe.mall.pay.support.OrderClientWrapper;
import com.zhe.mall.pay.support.PayChannelRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款审核流单测（12.24 / 28.7）。
 *
 * <p>重点覆盖三条边界：
 * <ol>
 *   <li>{@code audit=true} 建单后<b>不碰渠道、不改支付单</b>；</li>
 *   <li>同意/驳回都是 CAS，重复点击必须幂等而不是重复退款；</li>
 *   <li>客服能看不能审，身份缺失按 401 拒绝。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RefundAuditServiceTest {

    private static final String ORDER_NO = "A100";

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderClientWrapper orderClient;

    @Mock
    private PayChannelRegistry channelRegistry;

    @Mock
    private PayChannel channel;

    @InjectMocks
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("audit=true：只建待审核单，不调渠道、不改支付单与订单")
    void auditApplyOnlyCreatesPendingRefund() {
        when(orderClient.getPayView(ORDER_NO)).thenReturn(paidOrder());
        when(refundMapper.selectActiveByOrderNo(ORDER_NO)).thenReturn(null);
        when(paymentMapper.selectLatestPaidByOrderNo(ORDER_NO)).thenReturn(paidPayment());

        var vo = refundService.apply(new RefundApplyRequest(ORDER_NO, "不想要了", true), 10002L);

        assertThat(vo.status()).isEqualTo(0);
        assertThat(vo.statusLabel()).isEqualTo("待审核");
        verify(channelRegistry, never()).byCode(anyString());
        verify(channel, never()).refund(anyString(), anyString(), any());
        verify(paymentMapper, never()).markRefunded(anyString(), anyInt(), anyInt());
        verify(orderClient, never()).notifyRefund(anyString(), any());
    }

    @Test
    @DisplayName("audit=false：保持即时退款，申请后直接调渠道并推进支付单")
    void instantRefundStillCallsChannel() {
        when(orderClient.getPayView(ORDER_NO)).thenReturn(paidOrder());
        when(refundMapper.selectActiveByOrderNo(ORDER_NO)).thenReturn(null);
        when(paymentMapper.selectLatestPaidByOrderNo(ORDER_NO)).thenReturn(paidPayment());
        when(channelRegistry.byCode("MOCK")).thenReturn(channel);
        when(channel.refund(anyString(), anyString(), any())).thenReturn(ChannelRefundResult.ok("CH-1"));
        when(refundMapper.selectByRefundNo(anyString())).thenAnswer(inv -> refund(20, inv.getArgument(0)));

        var vo = refundService.apply(new RefundApplyRequest(ORDER_NO, "不想要了", false), 10002L);

        assertThat(vo.status()).isEqualTo(20);
        verify(channel).refund(anyString(), anyString(), any());
        verify(paymentMapper).markRefunded(eq("P100"), eq(20), eq(50));
    }

    @Test
    @DisplayName("同意：0 → 10 → 渠道成功 → 20，并回写审核人")
    void approveMovesPendingToSuccess() {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of("OPERATOR")));
        when(refundMapper.selectByRefundNo("R1"))
                .thenReturn(refund(0, "R1"))
                .thenReturn(refund(20, "R1"));
        when(paymentMapper.selectLatestPaidByOrderNo(ORDER_NO)).thenReturn(paidPayment());
        when(refundMapper.markApproved(eq("R1"), eq(0), eq(10), eq("operator01"), eq(10003L), any()))
                .thenReturn(1);
        when(channelRegistry.byCode("MOCK")).thenReturn(channel);
        when(channel.refund(anyString(), anyString(), any())).thenReturn(ChannelRefundResult.ok("CH-R1"));

        var vo = refundService.approve("R1");

        assertThat(vo.status()).isEqualTo(20);
        verify(refundMapper).markSuccess(eq("R1"), eq(10), eq(20), eq("CH-R1"), any());
        verify(paymentMapper).markRefunded(eq("P100"), eq(20), eq(50));
        // 两次通知是设计口径：先"退款中"，渠道成功后再"已退款"。
        verify(orderClient, times(2)).notifyRefund(eq(ORDER_NO), any());
    }

    @Test
    @DisplayName("驳回：0 → 30，原因落库，不调渠道、不动订单")
    void rejectWritesFailedWithoutChannel() {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of("OPERATOR")));
        when(refundMapper.selectByRefundNo("R1"))
                .thenReturn(refund(0, "R1"))
                .thenReturn(refund(30, "R1"));
        when(refundMapper.markRejected(eq("R1"), eq(0), eq(30), eq("商品已签收"), eq("operator01"),
                eq(10003L), any(), any())).thenReturn(1);

        var vo = refundService.reject("R1", "商品已签收");

        assertThat(vo.status()).isEqualTo(30);
        verify(channelRegistry, never()).byCode(anyString());
        verify(paymentMapper, never()).markRefunded(anyString(), anyInt(), anyInt());
        verify(orderClient, never()).notifyRefund(anyString(), any());
    }

    @Test
    @DisplayName("客服不能审：CS 角色即使有身份也被拒绝，且不改状态")
    void csCannotApprove() {
        UserContext.set(new CurrentUser(10004L, "cs01", List.of("CS")));
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(0, "R1"));

        assertThatThrownBy(() -> refundService.approve("R1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(refundMapper, never()).markApproved(anyString(), anyInt(), anyInt(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("匿名不能审：没有登录上下文按 401 拒绝")
    void anonymousCannotApprove() {
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(0, "R1"));

        assertThatThrownBy(() -> refundService.approve("R1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("重复同意：已是成功单直接回放，不再调渠道")
    void approveIsIdempotentOnSuccess() {
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(20, "R1"));

        var vo = refundService.approve("R1");

        assertThat(vo.status()).isEqualTo(20);
        assertThat(vo.idempotentReplay()).isTrue();
        verify(channel, never()).refund(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("重复驳回：已是失败单直接回放")
    void rejectIsIdempotentOnFailed() {
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(30, "R1"));

        var vo = refundService.reject("R1", "again");

        assertThat(vo.status()).isEqualTo(30);
        assertThat(vo.idempotentReplay()).isTrue();
        verify(refundMapper, never()).markRejected(anyString(), anyInt(), anyInt(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("非待审核单不能审：已驳回的单返回 35006，避免二次审批")
    void cannotApproveRefundingRefund() {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of("OPERATOR")));
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(30, "R1"));

        assertThatThrownBy(() -> refundService.approve("R1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.REFUND_STATUS_ILLEGAL.getCode());
    }

    @Test
    @DisplayName("已退款中：同意按幂等回放，不重复调渠道")
    void approveOnRefundingIsIdempotent() {
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(10, "R1"));

        var vo = refundService.approve("R1");

        assertThat(vo.status()).isEqualTo(10);
        assertThat(vo.idempotentReplay()).isTrue();
        verify(channel, never()).refund(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("审核通过前支付单已不是已支付：拒绝调渠道")
    void approveRejectsWhenPaymentAlreadyRefunded() {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of("OPERATOR")));
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund(0, "R1"));
        Payment refunded = paidPayment();
        refunded.setStatus(50);
        when(paymentMapper.selectLatestPaidByOrderNo(ORDER_NO)).thenReturn(refunded);

        assertThatThrownBy(() -> refundService.approve("R1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PAYMENT_STATUS_ILLEGAL.getCode());
        verify(channel, never()).refund(anyString(), anyString(), any());
    }

    private OrderPayView paidOrder() {
        return new OrderPayView(ORDER_NO, 10002L, 20, "已支付", new BigDecimal("99.00"), 1, null);
    }

    private Payment paidPayment() {
        Payment payment = new Payment();
        payment.setPaymentNo("P100");
        payment.setOrderNo(ORDER_NO);
        payment.setUserId(10002L);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(20);
        payment.setChannel("MOCK");
        return payment;
    }

    private Refund refund(int status, String refundNo) {
        return refund(status, refundNo, true);
    }

    private Refund refund(int status, String refundNo, boolean moveWhenApproved) {
        Refund refund = new Refund();
        refund.setRefundNo(refundNo);
        refund.setPaymentNo("P100");
        refund.setOrderNo(ORDER_NO);
        refund.setUserId(10002L);
        refund.setAmount(new BigDecimal("99.00"));
        refund.setStatus(status);
        refund.setOperator("user");
        refund.setOperatorId(10002L);
        return refund;
    }
}
