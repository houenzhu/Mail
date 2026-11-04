package com.zhe.mall.pay.service;

import com.zhe.mall.api.dto.AdminRefundDetail;
import com.zhe.mall.api.dto.AdminRefundPageQuery;
import com.zhe.mall.api.dto.AdminRefundRow;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.pay.entity.Refund;
import com.zhe.mall.pay.mapper.RefundMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 后台退款查询单测：分页透传、日期边界、状态校验与详情错误路径。 */
@ExtendWith(MockitoExtension.class)
class AdminRefundQueryServiceTest {

    @Mock
    private RefundMapper refundMapper;

    @InjectMocks
    private AdminRefundQueryService adminRefundQueryService;

    @Test
    @DisplayName("分页：日期结束日补到 23:59:59.999，状态标签由枚举生成")
    void pageNormalizesEndOfDayAndLabels() {
        when(refundMapper.countAdminPage(any())).thenReturn(1L);
        when(refundMapper.selectAdminPage(any(), anyInt(), anyInt())).thenReturn(List.of(refund()));

        PageResult<AdminRefundRow> result = adminRefundQueryService.page(
                new AdminRefundPageQuery(null, "A001", null, 20,
                        "2026-09-01", "2026-09-19", 1, 10));

        ArgumentCaptor<AdminRefundPageQuery> captor = ArgumentCaptor.forClass(AdminRefundPageQuery.class);
        verify(refundMapper).countAdminPage(captor.capture());
        assertThat(captor.getValue().createdAtStart()).isEqualTo("2026-09-01T00:00");
        assertThat(captor.getValue().createdAtEnd()).isEqualTo("2026-09-19T23:59:59.999");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).statusLabel()).isEqualTo("退款成功");
    }

    @Test
    @DisplayName("分页：空 body 补默认分页，total=0 时不再查列表")
    void pageDefaultsAndSkipsListWhenEmpty() {
        when(refundMapper.countAdminPage(any())).thenReturn(0L);

        PageResult<AdminRefundRow> result = adminRefundQueryService.page(null);

        assertThat(result.pageNo()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.list()).isEmpty();
        verify(refundMapper, never()).selectAdminPage(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("分页：非法状态码报 10003，不把 999 丢给 SQL")
    void pageRejectsUnknownStatus() {
        assertThatThrownBy(() -> adminRefundQueryService.page(
                new AdminRefundPageQuery(null, null, null, 999, null, null, 1, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_INVALID.getCode());
        verify(refundMapper, never()).countAdminPage(any());
    }

    @Test
    @DisplayName("分页：非法时间格式报 10003，不静默忽略筛选")
    void pageRejectsBadTime() {
        assertThatThrownBy(() -> adminRefundQueryService.page(
                new AdminRefundPageQuery(null, null, null, null, "2026/09/01", null, 1, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("详情：退款单不存在返回 35005")
    void detailNotFound() {
        when(refundMapper.selectByRefundNo("NOPE")).thenReturn(null);

        assertThatThrownBy(() -> adminRefundQueryService.detail("NOPE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.REFUND_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("详情：空单号直接拒绝，不打数据库")
    void detailRejectsBlank() {
        assertThatThrownBy(() -> adminRefundQueryService.detail("  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
        verify(refundMapper, never()).selectByRefundNo(anyString());
    }

    @Test
    @DisplayName("详情：返回完整审计字段")
    void detailReturnsAuditFields() {
        when(refundMapper.selectByRefundNo("R1")).thenReturn(refund());

        AdminRefundDetail detail = adminRefundQueryService.detail("R1");

        assertThat(detail.refund().refundNo()).isEqualTo("R1");
        assertThat(detail.refund().paymentNo()).isEqualTo("P1");
        assertThat(detail.refund().operator()).isEqualTo("user");
        assertThat(detail.refund().channelRefundNo()).isEqualTo("CH-R1");
    }

    private Refund refund() {
        Refund refund = new Refund();
        refund.setRefundNo("R1");
        refund.setPaymentNo("P1");
        refund.setOrderNo("A001");
        refund.setUserId(10002L);
        refund.setAmount(new BigDecimal("499.00"));
        refund.setStatus(20);
        refund.setReason("不想要了");
        refund.setOperator("user");
        refund.setOperatorId(10002L);
        refund.setChannelRefundNo("CH-R1");
        return refund;
    }
}
