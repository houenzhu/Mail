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
 * 发货写链路的单测。
 *
 * <p>发货是"状态 + 物流"两件事，重点验证：
 * <ul>
 *   <li>只有已支付能发；未支付/已取消走状态机拒绝（34002）；</li>
 *   <li>重复发货幂等：同单号直接返回，不重复写状态日志；</li>
 *   <li>并发 CAS 失败后重新读状态，只有"确实已发货且单号一致"才算幂等；</li>
 *   <li>操作人取网关注入身份，而不是请求体。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderShipServiceTest {

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
        UserContext.set(new CurrentUser(10003L, "operator01", List.of("OPERATOR")));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("发货成功：20 → 30，物流信息与操作人随 CAS 一起写入")
    void shipSuccess() {
        when(orderMapper.selectByOrderNo("A001")).thenReturn(order("A001", 20));
        when(orderMapper.markShipped(eq("A001"), eq(20), eq(30),
                eq("顺丰"), eq("SF123"), any(), eq("operator01"))).thenReturn(1);

        orderService.ship("A001", " 顺丰 ", " SF123 ");

        verify(orderMapper).markShipped(eq("A001"), eq(20), eq(30),
                eq("顺丰"), eq("SF123"), any(), eq("operator01"));
        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo("admin");
        assertThat(captor.getValue().getRemark()).contains("顺丰").contains("SF123");
    }

    @Test
    @DisplayName("未支付订单不能发货：状态机拒绝 34002")
    void shipRejectsUnpaid() {
        when(orderMapper.selectByOrderNo("A002")).thenReturn(order("A002", 10));

        assertThatThrownBy(() -> orderService.ship("A002", "顺丰", "SF123"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ORDER_STATUS_ILLEGAL.getCode());
        verify(orderMapper, never()).markShipped(anyString(), anyInt(), anyInt(),
                anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("重复发货同单号：幂等返回，不写第二条状态日志")
    void shipIsIdempotentForSameTrackingNo() {
        Order shipped = order("A003", 30);
        shipped.setTrackingNo("SF123");
        when(orderMapper.selectByOrderNo("A003")).thenReturn(shipped);

        orderService.ship("A003", "顺丰", "SF123");

        verify(orderMapper, never()).markShipped(anyString(), anyInt(), anyInt(),
                anyString(), anyString(), any(), anyString());
        verify(orderStatusLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("已发货改单号：拒绝，提示走售后")
    void shipRejectsChangingTrackingNo() {
        Order shipped = order("A004", 30);
        shipped.setTrackingNo("SF123");
        when(orderMapper.selectByOrderNo("A004")).thenReturn(shipped);

        assertThatThrownBy(() -> orderService.ship("A004", "顺丰", "SF999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号不可修改");
    }

    @Test
    @DisplayName("并发 CAS 失败：重新读到已发货且单号一致时按幂等处理")
    void shipConcurrentIdempotent() {
        when(orderMapper.selectByOrderNo("A005")).thenReturn(order("A005", 20));
        when(orderMapper.markShipped(eq("A005"), eq(20), eq(30),
                anyString(), anyString(), any(), anyString())).thenReturn(0);
        Order latest = order("A005", 30);
        latest.setTrackingNo("SF123");
        when(orderMapper.selectByOrderNo("A005")).thenReturn(order("A005", 20), latest);

        orderService.ship("A005", "顺丰", "SF123");

        verify(orderStatusLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("无 OPERATOR 角色：服务层防御性拒绝 20003")
    void shipRejectsNonOperator() {
        UserContext.set(new CurrentUser(10001L, "cs01", List.of("CS")));

        assertThatThrownBy(() -> orderService.ship("A006", "顺丰", "SF123"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(orderMapper, never()).selectByOrderNo(anyString());
    }

    @Test
    @DisplayName("物流公司或单号为空：10002，不打数据库")
    void shipRejectsBlankFields() {
        assertThatThrownBy(() -> orderService.ship("A007", "  ", "SF123"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
        assertThatThrownBy(() -> orderService.ship("A007", "顺丰", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
        verify(orderMapper, never()).selectByOrderNo(anyString());
    }

    private Order order(String orderNo, int status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(10002L);
        order.setStatus(status);
        return order;
    }
}
