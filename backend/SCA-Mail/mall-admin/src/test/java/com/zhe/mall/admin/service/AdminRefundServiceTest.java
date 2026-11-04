package com.zhe.mall.admin.service;

import com.zhe.mall.api.PayApi;
import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 退款 BFF 单测：转发、默认分页、错误码透传，不做降级。 */
@ExtendWith(MockitoExtension.class)
class AdminRefundServiceTest {

    @Mock
    private PayApi payApi;

    @InjectMocks
    private AdminRefundService adminRefundService;

    @Test
    @DisplayName("分页：原样转发条件并返回下游数据")
    void pageForwardsQuery() {
        AdminRefundPageQuery query = new AdminRefundPageQuery(
                "R1", "A001", 10002L, 20, "2026-09-01", "2026-09-19", 2, 20);
        when(payApi.adminRefundPage(query))
                .thenReturn(Result.success(PageResult.of(1, 2, 20, List.of())));

        PageResult<AdminRefundRow> result = adminRefundService.page(query);

        assertThat(result.total()).isEqualTo(1);
        verify(payApi).adminRefundPage(query);
    }

    @Test
    @DisplayName("分页：body 为空时补默认分页参数")
    void pageDefaultsWhenQueryMissing() {
        when(payApi.adminRefundPage(any())).thenReturn(Result.success(PageResult.empty(1, 10)));

        adminRefundService.page(null);

        ArgumentCaptor<AdminRefundPageQuery> captor = ArgumentCaptor.forClass(AdminRefundPageQuery.class);
        verify(payApi).adminRefundPage(captor.capture());
        assertThat(captor.getValue().safePageNo()).isEqualTo(1);
        assertThat(captor.getValue().safePageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("分页：下游失败必须上抛，不能降级成空列表")
    void pagePropagatesFailure() {
        when(payApi.adminRefundPage(any()))
                .thenReturn(Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "mall-pay 不可用"));

        assertThatThrownBy(() -> adminRefundService.page(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mall-pay 不可用");
    }

    @Test
    @DisplayName("详情：空单号直接拒绝，不打下游")
    void detailRejectsBlank() {
        assertThatThrownBy(() -> adminRefundService.detail(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
        verify(payApi, never()).adminRefundDetail(any());
    }

    @Test
    @DisplayName("详情：透传下游 35005")
    void detailPropagatesNotFound() {
        when(payApi.adminRefundDetail("NOPE"))
                .thenReturn(Result.failure(ResultCode.REFUND_NOT_FOUND, "退款单不存在"));

        assertThatThrownBy(() -> adminRefundService.detail("NOPE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.REFUND_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("详情：成功时原样返回")
    void detailReturnsAggregate() {
        AdminRefundDetail detail = new AdminRefundDetail(null);
        when(payApi.adminRefundDetail("R1")).thenReturn(Result.success(detail));

        assertThat(adminRefundService.detail("R1")).isSameAs(detail);
    }
}
