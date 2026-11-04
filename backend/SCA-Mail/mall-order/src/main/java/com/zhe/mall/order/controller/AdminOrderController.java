package com.zhe.mall.order.controller;

import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.api.dto.AdminShipRequest;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.order.service.AdminOrderQueryService;
import com.zhe.mall.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单查询接口（mall-admin 经 Feign 调用）。
 *
 * <p>与 C 端 {@link OrderController} 分开挂载：C 端接口的 {@code @CurrentUserId} 语义是
 * "本人"，而这里的管理端接口是全量视角。角色校验用 {@code @RequireStaff}，
 * 依据是网关透传的可信身份头；直连服务或伪造身份头会被判为匿名（fail-closed）。
 *
 * <p>路径前缀 {@code /admin} 与商品管理接口保持一致，网关的公开白名单不覆盖这些路径。
 */
@RestController
@RequestMapping("/api/order/admin")
@RequireStaff
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    private final OrderService orderService;

    public AdminOrderController(AdminOrderQueryService adminOrderQueryService, OrderService orderService) {
        this.adminOrderQueryService = adminOrderQueryService;
        this.orderService = orderService;
    }

    /** 后台订单分页：全量订单，支持订单号 / 买家 / 状态 / 创建时间区间筛选。 */
    @PostMapping("/page")
    public Result<PageResult<AdminOrderRow>> page(@RequestBody(required = false) AdminOrderPageQuery query) {
        return Result.success(adminOrderQueryService.page(query));
    }

    /** 后台订单详情：订单主体 + 明细 + 状态流转时间线。 */
    @GetMapping("/{orderNo}")
    public Result<AdminOrderDetail> detail(@PathVariable("orderNo") String orderNo) {
        return Result.success(adminOrderQueryService.detail(orderNo));
    }

    /**
     * 发货：已支付 → 已发货（UC-ORDER-07）。
     *
     * <p>操作级校验用 {@code @RequireStaff(OPERATOR)}：客服能看单但不能发货，
     * 管理员按角色包含关系覆盖运营（见 {@code CurrentUser#hasRole}）。
     */
    @PostMapping("/{orderNo}/ship")
    @RequireStaff(UserRole.OPERATOR)
    public Result<AdminOrderDetail> ship(@PathVariable("orderNo") String orderNo,
                                         @Valid @RequestBody AdminShipRequest request) {
        orderService.ship(orderNo, request.shippingCompany(), request.trackingNo());
        return Result.success(adminOrderQueryService.detail(orderNo));
    }
}
