package com.zhe.mall.order.service;

import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.order.dto.OrderItemStat;
import com.zhe.mall.order.entity.Order;
import com.zhe.mall.order.entity.OrderItem;
import com.zhe.mall.order.entity.OrderStatusLog;
import com.zhe.mall.order.mapper.OrderItemMapper;
import com.zhe.mall.order.mapper.OrderMapper;
import com.zhe.mall.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台订单查询的单测。
 *
 * <p>重点覆盖三类容易出错的地方：
 * <ul>
 *   <li>列表的件数/数量必须是**一次批量查询**拿到的，不能逐单查（N+1）；</li>
 *   <li>时间筛选的边界：只传日期时结束日要补到当天 23:59:59.999，否则当天订单会被漏掉；</li>
 *   <li>错误路径：非法状态码/非法时间必须报参数错误，而不是静默忽略条件。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderQueryServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;

    @InjectMocks
    private AdminOrderQueryService service;

    @Test
    @DisplayName("分页：订单与明细统计一次性批量合并，不逐单查询")
    void pageMergesItemStatsInOneBatch() {
        when(orderMapper.countAdminPage(any())).thenReturn(2L);
        when(orderMapper.selectAdminPage(any(), anyInt(), anyInt()))
                .thenReturn(List.of(order("A001", 20), order("A002", 10)));
        when(orderItemMapper.selectStatsByOrderNos(anyList()))
                .thenReturn(List.of(new OrderItemStat("A001", 2, 3),
                        new OrderItemStat("A002", 1, 1)));

        PageResult<AdminOrderRow> result = service.page(
                new AdminOrderPageQuery(null, null, null, null, null, 1, 10));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.list()).hasSize(2);
        assertThat(result.list().get(0).itemCount()).isEqualTo(2);
        assertThat(result.list().get(0).totalQty()).isEqualTo(3);
        assertThat(result.list().get(1).itemCount()).isEqualTo(1);
        assertThat(result.list().get(0).statusLabel()).isEqualTo("已支付");

        // 批量接口只调用一次：防止以后被改成逐单查询
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemMapper).selectStatsByOrderNos(captor.capture());
        assertThat(captor.getValue()).containsExactly("A001", "A002");
    }

    @Test
    @DisplayName("分页：空结果不查明细，避免无意义的下游调用")
    void emptyPageSkipsItemStats() {
        when(orderMapper.countAdminPage(any())).thenReturn(0L);

        PageResult<AdminOrderRow> result = service.page(null);

        assertThat(result.total()).isZero();
        assertThat(result.list()).isEmpty();
        verify(orderItemMapper, never()).selectStatsByOrderNos(anyList());
        verify(orderMapper, never()).selectAdminPage(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("时间筛选：只传日期时，开始补 00:00、结束补 23:59:59.999")
    void normalizesDateOnlyRangeToFullDay() {
        when(orderMapper.countAdminPage(any())).thenReturn(0L);

        service.page(new AdminOrderPageQuery(null, null, null,
                "2026-11-01", "2026-11-04", 1, 10));

        ArgumentCaptor<AdminOrderPageQuery> captor = ArgumentCaptor.forClass(AdminOrderPageQuery.class);
        verify(orderMapper).countAdminPage(captor.capture());
        AdminOrderPageQuery effective = captor.getValue();
        assertThat(effective.createdAtStart()).startsWith("2026-11-01T00:00");
        assertThat(effective.createdAtEnd()).startsWith("2026-11-04T23:59:59.999");
    }

    @Test
    @DisplayName("时间筛选：非法时间报参数错误，而不是静默忽略条件")
    void rejectsIllegalTimeFormat() {
        assertThatThrownBy(() -> service.page(new AdminOrderPageQuery(
                null, null, null, "2026/11/01", null, 1, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时间格式");
    }

    @Test
    @DisplayName("状态筛选：未知状态码直接拒绝")
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> service.page(new AdminOrderPageQuery(
                null, null, 999, null, null, 1, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("详情：返回订单主体、明细与状态时间线")
    void detailReturnsItemsAndTimeline() {
        Order order = order("A001", 20);
        when(orderMapper.selectByOrderNo("A001")).thenReturn(order);
        when(orderItemMapper.selectByOrderNo("A001")).thenReturn(List.of(item("A001")));
        when(orderStatusLogMapper.selectByOrderNo("A001")).thenReturn(List.of(
                log(10, 20), log(20, 30)));

        AdminOrderDetail detail = service.detail("A001");

        assertThat(detail.order().orderNo()).isEqualTo("A001");
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().get(0).productName()).isEqualTo("测试商品");
        assertThat(detail.logs()).hasSize(2);
        assertThat(detail.logs().get(0).toStatusLabel()).isEqualTo("已支付");
        assertThat(detail.order().itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("详情：订单不存在返回 34001，空订单号返回参数错误")
    void detailRejectsMissingOrder() {
        when(orderMapper.selectByOrderNo("NOPE")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("NOPE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ORDER_NOT_FOUND.getCode());

        assertThatThrownBy(() -> service.detail(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
    }

    @Test
    @DisplayName("时间线：遇到未知状态码时用原始码兜底，不抛异常")
    void timelineToleratesUnknownStatusCode() {
        when(orderMapper.selectByOrderNo("A001")).thenReturn(order("A001", 20));
        when(orderItemMapper.selectByOrderNo("A001")).thenReturn(List.of());
        when(orderStatusLogMapper.selectByOrderNo("A001")).thenReturn(List.of(log(10, 99)));

        AdminOrderDetail detail = service.detail("A001");

        assertThat(detail.logs().get(0).toStatusLabel()).isEqualTo("99");
    }

    private Order order(String orderNo, int status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(10001L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("499.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("499.00"));
        order.setPayType(1);
        order.setReceiverName("张三");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("上海市浦东新区");
        order.setExpireAt(LocalDateTime.now().plusMinutes(30));
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private OrderItem item(String orderNo) {
        OrderItem item = new OrderItem();
        item.setOrderNo(orderNo);
        item.setSkuId(1001L);
        item.setProductId(1L);
        item.setProductName("测试商品");
        item.setSkuSpec("白色");
        item.setPrice(new BigDecimal("499.00"));
        item.setQty(1);
        item.setTotalAmount(new BigDecimal("499.00"));
        return item;
    }

    private OrderStatusLog log(int from, int to) {
        OrderStatusLog log = new OrderStatusLog();
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperator("pay");
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}
