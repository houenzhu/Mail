package com.zhe.mall.order.controller;

import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.PayCallbackRequest;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.common.auth.AuthHeaders;
import com.zhe.mall.common.auth.InternalTokenVerifier;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单内部接口：只给 mall-pay 直连调用（ADR-022 / 12.22）。
 *
 * <p>这是 V1.7 定稿的 P0 修复：原 {@code POST /api/order/{orderNo}/pay-callback} 是公开路径 + 只要求登录，
 * 任何买家都能把自己的待支付订单直接标记为已支付。现在回调全部收敛到 {@code /api/order/internal/**}：
 * <ul>
 *   <li>网关一刀切拦截含 {@code /internal/} 的外部访问（403）；</li>
 *   <li>服务端再校验一次 {@code X-Gateway-Token}，直连服务同样无法伪造。</li>
 * </ul>
 *
 * <p><b>凭据先校验、后查单</b>：未带令牌时直接返回 20003，不暴露订单是否存在，避免变成订单号探测接口。
 */
@RestController
@RequestMapping("/api/order/internal")
public class OrderInternalController {

    private final OrderService orderService;

    private final InternalTokenVerifier internalTokenVerifier;

    public OrderInternalController(OrderService orderService, InternalTokenVerifier internalTokenVerifier) {
        this.orderService = orderService;
        this.internalTokenVerifier = internalTokenVerifier;
    }

    /** 订单支付视图：支付侧发起支付 / 申请退款前的校验入口。 */
    @GetMapping("/{orderNo}/pay-view")
    public Result<OrderPayView> payView(@PathVariable("orderNo") String orderNo,
                                        @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false)
                                        String internalToken) {
        internalTokenVerifier.verify(internalToken);
        return Result.success(orderService.payView(orderNo));
    }

    /** 支付成功回调：订单 10 → 20，幂等。 */
    @PostMapping("/{orderNo}/pay-callback")
    public Result<OrderPayView> payCallback(@PathVariable("orderNo") String orderNo,
                                            @RequestBody PayCallbackRequest request,
                                            @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false)
                                            String internalToken) {
        internalTokenVerifier.verify(internalToken);
        return Result.success(orderService.payCallback(orderNo, request));
    }

    /** 退款阶段回调：REFUNDING / REFUNDED / REJECTED，按当前状态幂等。 */
    @PostMapping("/{orderNo}/refund-callback")
    public Result<OrderPayView> refundCallback(@PathVariable("orderNo") String orderNo,
                                               @RequestBody RefundCallbackRequest request,
                                               @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false)
                                               String internalToken) {
        internalTokenVerifier.verify(internalToken);
        return Result.success(orderService.refundCallback(orderNo, request));
    }
}
