package com.zhe.mall.pay.controller;

import com.zhe.mall.common.auth.CurrentUserId;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.pay.dto.CallbackResultVO;
import com.zhe.mall.pay.dto.PayCreateRequest;
import com.zhe.mall.pay.dto.PayCreateVO;
import com.zhe.mall.pay.dto.PaymentVO;
import com.zhe.mall.pay.dto.RefundApplyRequest;
import com.zhe.mall.pay.dto.RefundVO;
import com.zhe.mall.pay.service.PaymentService;
import com.zhe.mall.pay.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付对外接口（28.4）。
 *
 * <p>用户身份一律来自网关注入的登录上下文（{@code @CurrentUserId}），不信任请求参数里的 userId：
 * 越权在 21.3 是明确的防御重点，支付侧尤其不能靠"客户端说自己是谁"。
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PaymentService paymentService;

    private final RefundService refundService;

    public PayController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    /** 发起支付（幂等键 requestId）。 */
    @PostMapping
    public Result<PayCreateVO> create(@Valid @RequestBody PayCreateRequest request,
                                      @CurrentUserId Long userId) {
        return Result.success(paymentService.create(request, userId));
    }

    /** 申请退款（限已支付未发货、全额）。 */
    @PostMapping("/refund")
    public Result<RefundVO> refund(@Valid @RequestBody RefundApplyRequest request,
                                   @CurrentUserId Long userId) {
        return Result.success(refundService.apply(request, userId));
    }

    /** 退款单查询。 */
    @GetMapping("/refund/{refundNo}")
    public Result<RefundVO> refundDetail(@PathVariable("refundNo") String refundNo,
                                         @CurrentUserId Long userId) {
        return Result.success(refundService.get(refundNo, userId));
    }

    /**
     * 模拟支付成功（<b>仅 Mock 通道</b>）：让买家在演示 / 联调环境把支付闭环走完。
     *
     * <p>安全边界：
     * <ol>
     *   <li>必须登录，且 {@code PaymentService} 内会校验支付单归属（他人支付单 20003）；</li>
     *   <li>只有当前配置的渠道确实是 MOCK 才允许，生产切到真实渠道后固定返回 35007；</li>
     *   <li>不伪造签名、不绕过支付单状态机 —— 内部走的是既有的
     *       {@code sync()} 主动查询通道，渠道状态仍由 {@code PayChannel} 决定。</li>
     * </ol>
     */
    @PostMapping("/mock/{paymentNo}/pay")
    public Result<CallbackResultVO> mockPay(@PathVariable("paymentNo") String paymentNo,
                                            @CurrentUserId Long userId) {
        return Result.success(paymentService.mockPay(paymentNo, userId));
    }

    /** 支付单查询。 */
    @GetMapping("/{paymentNo}")
    public Result<PaymentVO> detail(@PathVariable("paymentNo") String paymentNo,
                                    @CurrentUserId Long userId) {
        return Result.success(paymentService.get(paymentNo, userId));
    }
}
