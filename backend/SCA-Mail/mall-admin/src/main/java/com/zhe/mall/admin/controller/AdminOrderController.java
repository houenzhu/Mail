package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.service.AdminOrderService;
import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.api.dto.AdminShipRequest;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单管理接口（对外统一 {@code /api/admin/order/**}）。
 *
 * <p>鉴权与商品/库存一致：网关保证会话有效，BFF 控制器要求 staff，
 * 下游 {@code mall-order} 的 {@code /api/order/admin/**} 再校验一次 staff，形成双层边界。
 *
 * <p>查询（分页 + 详情）对任意 staff 开放；发货额外要求 OPERATOR，
 * 并在下游 {@code mall-order} 再校验一次，形成双层边界。
 */
@RestController
@RequestMapping("/api/admin/order")
@RequireStaff
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    /**
     * 后台订单分页。
     *
     * <p>用 POST + body：筛选条件多（订单号 / 买家 / 状态 / 时间区间）且大多可选，
     * body 比一长串 query 更清晰，也与商品分页的契约风格一致。
     */
    @PostMapping("/page")
    public Result<PageResult<AdminOrderRow>> page(
            @RequestBody(required = false) AdminOrderPageQuery query) {
        return Result.success(adminOrderService.page(query));
    }

    /** 后台订单详情：订单主体 + 明细 + 状态流转时间线。 */
    @GetMapping("/{orderNo}")
    public Result<AdminOrderDetail> detail(@PathVariable("orderNo") String orderNo) {
        return Result.success(adminOrderService.detail(orderNo));
    }

    /** 发货：已支付 → 已发货，写入物流公司与单号（UC-ORDER-07）。 */
    @PostMapping("/{orderNo}/ship")
    @RequireStaff(UserRole.OPERATOR)
    public Result<AdminOrderDetail> ship(@PathVariable("orderNo") String orderNo,
                                         @Valid @RequestBody AdminShipRequest request) {
        return Result.success(adminOrderService.ship(orderNo, request));
    }

    /** 代客确认收货：已发货 → 已完成（UC-ORDER-06），仅 OPERATOR/ADMIN。 */
    @PostMapping("/{orderNo}/confirm")
    @RequireStaff(UserRole.OPERATOR)
    public Result<AdminOrderDetail> confirm(@PathVariable("orderNo") String orderNo) {
        return Result.success(adminOrderService.confirm(orderNo));
    }
}
