package com.zhe.mall.api;

import com.zhe.mall.api.dto.AdminOrderDetail;
import com.zhe.mall.api.dto.AdminDashboardOverview;
import com.zhe.mall.api.dto.AdminDashboardTrendPoint;
import com.zhe.mall.api.dto.AdminOrderPageQuery;
import com.zhe.mall.api.dto.AdminOrderRow;
import com.zhe.mall.api.dto.AdminShipRequest;
import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.PayCallbackRequest;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 订单服务对外契约（mall-pay → mall-order）。
 *
 * <p>三个接口全部是**内部接口**（路径含 {@code /internal/}）：调用方必须携带 {@code X-Gateway-Token}，
 * 网关一刀切拦截外部访问（ADR-022 / 12.22）。这样做的原因是原公开回调路径
 * {@code POST /api/order/{orderNo}/pay-callback} 只要求登录，任何买家都能把自己的待支付订单标记为已支付。
 */
@FeignClient(name = "mall-order", path = "/api/order")
public interface OrderApi {

    /**
     * 订单支付视图：支付侧发起支付 / 申请退款前校验订单状态、归属与应付金额。
     *
     * <p>不返回收货人等信息——支付侧只需要"能不能付、付多少、谁的单"。
     */
    @GetMapping("/internal/{orderNo}/pay-view")
    Result<OrderPayView> getPayView(@PathVariable("orderNo") String orderNo);

    /** 支付成功回调：订单 10 → 20（幂等，重复回调返回当前订单）。 */
    @PostMapping("/internal/{orderNo}/pay-callback")
    Result<OrderPayView> payCallback(@PathVariable("orderNo") String orderNo,
                                     @RequestBody PayCallbackRequest request);

    /** 退款阶段回调：订单 20 ⇄ 60 → 70（阶段由 {@link com.zhe.mall.api.dto.RefundStage} 表达）。 */
    @PostMapping("/internal/{orderNo}/refund-callback")
    Result<OrderPayView> refundCallback(@PathVariable("orderNo") String orderNo,
                                        @RequestBody RefundCallbackRequest request);

    /* ---------------- 后台管理接口（mall-admin 经 Feign 调用，服务端要求 staff） ---------------- */

    /**
     * 后台订单分页：不按用户过滤，支持订单号 / 买家 / 状态 / 创建时间区间筛选。
     *
     * <p>用 POST + body：筛选条件多且大多可选，body 比一长串 query 更清晰。
     */
    @PostMapping("/admin/page")
    Result<PageResult<AdminOrderRow>> adminPage(@RequestBody AdminOrderPageQuery query);

    /** 后台订单详情：订单主体 + 明细 + 状态流转时间线（运营视角，不限订单归属）。 */
    @GetMapping("/admin/{orderNo}")
    Result<AdminOrderDetail> adminDetail(@PathVariable("orderNo") String orderNo);

    /**
     * 后台发货：已支付 → 已发货，并写入物流公司与单号（UC-ORDER-07）。
     *
     * <p>重复发货是幂等的：订单已经是已发货时返回当前详情，不重复写状态日志。
     */
    @PostMapping("/admin/{orderNo}/ship")
    Result<AdminOrderDetail> adminShip(@PathVariable("orderNo") String orderNo,
                                       @RequestBody AdminShipRequest request);

    /* ---------------- 后台看板（mall-admin 聚合时调用） ---------------- */

    /** 看板概览中的订单侧指标：今日 GMV / 今日订单数 / 待发货 / 退款中。 */
    @GetMapping("/admin/dashboard/overview")
    Result<AdminDashboardOverview> adminDashboardOverview();

    /**
     * 近 N 日趋势（日期升序，缺失日期补 0）。
     *
     * @param days 天数，服务端收敛到 1~90
     */
    @GetMapping("/admin/dashboard/trend")
    Result<List<AdminDashboardTrendPoint>> adminDashboardTrend(@RequestParam("days") Integer days);
}

