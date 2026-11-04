package com.zhe.mall.admin.service;

import com.zhe.mall.api.OrderApi;
import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台订单 BFF 的单测。
 *
 * <p>重点：BFF 只做转发与错误码翻译，**不做降级**——订单列表是核心视图，
 * 下游失败必须上抛，不能返回空列表伪装成功（这与商品列表的附加数据降级口径不同）。
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderApi orderApi;

    @InjectMocks
    private AdminOrderService adminOrderService;

    @Test
    @DisplayName("分页：原样转发查询条件并返回下游数据")
    void pageForwardsQuery() {
        AdminOrderPageQuery query = new AdminOrderPageQuery(
                "A001", 10002L, 20, "2026-09-01", "2026-09-19", 2, 20);
        AdminOrderRow row = new AdminOrderRow("A001", 10002L, 20, "已支付",
                new BigDecimal("499.00"), BigDecimal.ZERO, new BigDecimal("499.00"),
                1, "张三", "13800000000", "上海市", null,
                null, null, null, null, null, null, null, null, null, 1, 1);
        when(orderApi.adminPage(query))
                .thenReturn(Result.success(PageResult.of(1, 2, 20, List.of(row))));

        PageResult<AdminOrderRow> result = adminOrderService.page(query);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).orderNo()).isEqualTo("A001");
        verify(orderApi).adminPage(query);
    }

    @Test
    @DisplayName("分页：body 为空时补默认分页参数，不把 null 透传给下游")
    void pageDefaultsWhenQueryMissing() {
        when(orderApi.adminPage(any()))
                .thenReturn(Result.success(PageResult.empty(1, 10)));

        adminOrderService.page(null);

        org.mockito.ArgumentCaptor<AdminOrderPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(AdminOrderPageQuery.class);
        verify(orderApi).adminPage(captor.capture());
        assertThat(captor.getValue().safePageNo()).isEqualTo(1);
        assertThat(captor.getValue().safePageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("分页：下游业务失败必须上抛，不能降级成空列表")
    void pagePropagatesDownstreamFailure() {
        when(orderApi.adminPage(any()))
                .thenReturn(Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "mall-order 不可用"));

        assertThatThrownBy(() -> adminOrderService.page(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mall-order 不可用");
    }

    @Test
    @DisplayName("详情：订单号为空时直接拒绝，不打下游")
    void detailRejectsBlankOrderNo() {
        assertThatThrownBy(() -> adminOrderService.detail("  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());

        verify(orderApi, never()).adminDetail(any());
    }

    @Test
    @DisplayName("详情：透传下游的订单不存在错误码")
    void detailPropagatesNotFound() {
        when(orderApi.adminDetail("NOPE"))
                .thenReturn(Result.failure(ResultCode.ORDER_NOT_FOUND, "订单不存在"));

        assertThatThrownBy(() -> adminOrderService.detail("NOPE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ORDER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("详情：成功时返回主体、明细与时间线")
    void detailReturnsAggregate() {
        AdminOrderDetail detail = new AdminOrderDetail(null, List.of(), List.of());
        when(orderApi.adminDetail("A001")).thenReturn(Result.success(detail));

        assertThat(adminOrderService.detail("A001")).isSameAs(detail);
    }
}
