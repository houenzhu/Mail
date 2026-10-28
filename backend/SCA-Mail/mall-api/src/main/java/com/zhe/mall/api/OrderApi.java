package com.zhe.mall.api;

import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.PayCallbackRequest;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
}
