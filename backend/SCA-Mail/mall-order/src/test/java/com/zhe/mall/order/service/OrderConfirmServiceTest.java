package com.zhe.mall.order.service;

import com.zhe.mall.common.auth.CurrentUser;
import com.zhe.mall.common.auth.UserContext;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.mq.OutboxService;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.order.entity.Order;
import com.zhe.mall.order.entity.OrderStatusLog;
import com.zhe.mall.order.mapper.OrderItemMapper;
import com.zhe.mall.order.mapper.OrderMapper;
import com.zhe.mall.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 确认收货（UC-ORDER-06）写链路单测。
 *
 * <p>重点验证：
 * <ul>
 *   <li>只有 30 已发货能确认；未发货/已取消走状态机拒绝 34002；</li>
 *   <li>买家只能确认自己的订单，他人 20003；staff 代确认受 OPERATOR 限制；</li>
 *   <li>重复确认与并发 CAS 失败都是幂等返回，不写第二条状态日志；</li>
 *   <li>完成时间与状态在同一条 CAS 里写入。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderConfirmServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;

    @Mock
    private OutboxService outboxService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderMapper, orderItemMapper, orderStatusLogMapper,
                null, null, null, outboxService, 30L, 24L, 60L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("买家确认收货：30 → 40，完成时间随 CAS 写入，日志操作人为 user")
    void buyerConfirmSuccess() {
        when(orderMapper.selectByOrderNo("C001")).thenReturn(order("C001", 30, 10002L));
        when(orderMapper.markCompleted(eq("C001"), eq(30), eq(40), any())).thenReturn(1);

        orderService.confirm("C001", 10002L);

        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).markCompleted(eq("C001"), eq(30), eq(40), timeCaptor.capture());
        assertThat(timeCaptor.getValue()).isNotNull();

        ArgumentCaptor<OrderStatusLog> logCaptor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo(30);
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(40);
        assertThat(logCaptor.getValue().getOperator()).isEqualTo("user");
    }

    @Test
    @DisplayName("未发货订单不能确认：状态机拒绝 34002")
    void confirmRejectsUnshipped() {
        when(orderMapper.selectByOrderNo("C002")).thenReturn(order("C002", 20, 10002L));

        assertThatThrownBy(() -> orderService.confirm("C002", 10002L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ORDER_STATUS_ILLEGAL.getCode());
        verify(orderMapper, never()).markCompleted(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("他人订单不能确认：20003")
    void confirmRejectsOtherUser() {
        when(orderMapper.selectByOrderNo("C003")).thenReturn(order("C003", 30, 10009L));

        assertThatThrownBy(() -> orderService.confirm("C003", 10002L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(orderMapper, never()).markCompleted(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("重复确认：幂等返回，不写第二条状态日志")
    void confirmIsIdempotentWhenCompleted() {
        when(orderMapper.selectByOrderNo("C004")).thenReturn(order("C004", 40, 10002L));

        orderService.confirm("C004", 10002L);

        verify(orderMapper, never()).markCompleted(anyString(), anyInt(), anyInt(), any());
        verify(orderStatusLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("并发 CAS 失败：重新读到已完成按幂等处理")
    void confirmConcurrentIdempotent() {
        when(orderMapper.selectByOrderNo("C005")).thenReturn(order("C005", 30, 10002L));
        when(orderMapper.markCompleted(eq("C005"), eq(30), eq(40), any())).thenReturn(0);
        when(orderMapper.selectByOrderNo("C005"))
                .thenReturn(order("C005", 30, 10002L), order("C005", 40, 10002L));

        orderService.confirm("C005", 10002L);

        verify(orderStatusLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("CAS 失败且状态已被改成别的值：报 34002，不静默成功")
    void confirmCasConflictReportsError() {
        when(orderMapper.selectByOrderNo("C006")).thenReturn(order("C006", 30, 10002L));
        when(orderMapper.markCompleted(eq("C006"), eq(30), eq(40), any())).thenReturn(0);
        when(orderMapper.selectByOrderNo("C006"))
                .thenReturn(order("C006", 30, 10002L), order("C006", 60, 10002L));

        assertThatThrownBy(() -> orderService.confirm("C006", 10002L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变更");
    }

    @Test
    @DisplayName("后台代客确认：日志操作人为 admin")
    void staffConfirmSuccess() {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of("OPERATOR")));
        when(orderMapper.selectByOrderNo("C007")).thenReturn(order("C007", 30, 10002L));
        when(orderMapper.markCompleted(eq("C007"), eq(30), eq(40), any())).thenReturn(1);

        orderService.confirmByStaff("C007");

        ArgumentCaptor<OrderStatusLog> logCaptor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperator()).isEqualTo("admin");
    }

    @Test
    @DisplayName("客服不能代客确认：服务层防御性拒绝 20003")
    void staffConfirmRejectsNonOperator() {
        UserContext.set(new CurrentUser(10005L, "cs01", List.of("CS")));

        assertThatThrownBy(() -> orderService.confirmByStaff("C008"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(orderMapper, never()).selectByOrderNo(anyString());
    }

    @Test
    @DisplayName("无登录上下文：代客确认 20001")
    void staffConfirmRequiresLogin() {
        assertThatThrownBy(() -> orderService.confirmByStaff("C009"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    private Order order(String orderNo, int status, long userId) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStatus(status);
        return order;
    }
}
