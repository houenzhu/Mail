package com.zhe.mall.admin.service;

import com.zhe.mall.api.OrderApi;
import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminShipRequest;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发货 BFF 单测：只转发，不重复实现业务规则。 */
@ExtendWith(MockitoExtension.class)
class AdminOrderShipServiceTest {

    @Mock
    private OrderApi orderApi;

    @InjectMocks
    private AdminOrderService adminOrderService;

    @Test
    @DisplayName("发货：转发订单号与物流信息，返回下游详情")
    void shipForwards() {
        AdminShipRequest request = new AdminShipRequest("顺丰", "SF123");
        AdminOrderDetail detail = new AdminOrderDetail(null, null, null);
        when(orderApi.adminShip("A001", request)).thenReturn(Result.success(detail));

        assertThat(adminOrderService.ship(" A001 ", request)).isSameAs(detail);
        verify(orderApi).adminShip("A001", request);
    }

    @Test
    @DisplayName("发货：空订单号直接拒绝")
    void shipRejectsBlankOrderNo() {
        assertThatThrownBy(() -> adminOrderService.ship(" ", new AdminShipRequest("顺丰", "SF123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
        verify(orderApi, never()).adminShip(any(), any());
    }

    @Test
    @DisplayName("发货：空请求体直接拒绝")
    void shipRejectsNullRequest() {
        assertThatThrownBy(() -> adminOrderService.ship("A001", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PARAM_MISSING.getCode());
    }

    @Test
    @DisplayName("发货：下游状态非法透传 34002")
    void shipPropagatesStatusIllegal() {
        AdminShipRequest request = new AdminShipRequest("顺丰", "SF123");
        when(orderApi.adminShip("A001", request))
                .thenReturn(Result.failure(ResultCode.ORDER_STATUS_ILLEGAL, "订单状态不允许发货"));

        assertThatThrownBy(() -> adminOrderService.ship("A001", request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ORDER_STATUS_ILLEGAL.getCode());
    }
}
