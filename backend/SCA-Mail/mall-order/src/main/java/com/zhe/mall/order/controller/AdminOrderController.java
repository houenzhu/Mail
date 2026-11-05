package com.zhe.mall.order.controller;

import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.api.dto.AdminShipRequest;
import com.zhe.mall.api.dto.AdminDashboardOverview;
import com.zhe.mall.api.dto.AdminDashboardTrendPoint;
import com.zhe.mall.api.dto.AdminProductSalesStat;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.order.service.AdminOrderQueryService;
import com.zhe.mall.order.service.AdminOrderDashboardService;
import com.zhe.mall.order.service.AdminProductSalesQueryService;
import com.zhe.mall.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private final AdminOrderDashboardService adminOrderDashboardService;

    private final AdminProductSalesQueryService adminProductSalesQueryService;

    private final OrderService orderService;

    public AdminOrderController(AdminOrderQueryService adminOrderQueryService,
                                AdminOrderDashboardService adminOrderDashboardService,
                                OrderService orderService,
                                AdminProductSalesQueryService adminProductSalesQueryService) {
        this.adminOrderQueryService = adminOrderQueryService;
        this.adminOrderDashboardService = adminOrderDashboardService;
        this.orderService = orderService;
        this.adminProductSalesQueryService = adminProductSalesQueryService;
    }

    /*
     * 注意路由顺序：Spring 的路径匹配会让 /dashboard/... 优先于 /{orderNo}，
     * 但为避免以后有人把 orderNo 规则改成正则而误吞 dashbord 路径，
     * 这里把看板接口集中声明在分页与详情之前，语义上也更清楚。
     */

    /** 看板概览的订单侧指标。 */
    @GetMapping("/dashboard/overview")
    public Result<AdminDashboardOverview> dashboardOverview() {
        return Result.success(adminOrderDashboardService.overview());
    }

    /** 看板近 N 日趋势（日期升序，缺失日期补 0）。 */
    @GetMapping("/dashboard/trend")
    public Result<List<AdminDashboardTrendPoint>> dashboardTrend(
            @RequestParam(value = "days", required = false) Integer days) {
        return Result.success(adminOrderDashboardService.trend(days == null ? 7 : days));
    }

    /**
     * 批量查询商品近 N 日销量（G-13）。
     *
     * <p>用 POST + body 传商品 ID 列表：整页商品可能几十个，query 串容易超长；
     * 与商品分页、SKU 批量查询保持同一风格。
     *
     * @param days 天数，服务端收敛到 1~90，默认 30
     */
    @PostMapping("/product-sales")
    public Result<List<AdminProductSalesStat>> productSales(
            @RequestBody(required = false) List<Long> productIds,
            @RequestParam(value = "days", required = false) Integer days) {
        return Result.success(adminProductSalesQueryService.sales(productIds, days));
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

    /**
     * 后台代客确认收货：已发货 → 已完成（UC-ORDER-06）。
     *
     * <p>买家失联或物流长期无更新时由运营代确认；与买家侧共用同一状态机与幂等口径。
     * 客服只能看不代确认，因此限定 OPERATOR（ADMIN 按角色包含关系覆盖）。
     */
    @PostMapping("/{orderNo}/confirm")
    @RequireStaff(UserRole.OPERATOR)
    public Result<AdminOrderDetail> confirm(@PathVariable("orderNo") String orderNo) {
        orderService.confirmByStaff(orderNo);
        return Result.success(adminOrderQueryService.detail(orderNo));
    }
}
