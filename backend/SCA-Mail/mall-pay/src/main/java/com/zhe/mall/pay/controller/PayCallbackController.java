package com.zhe.mall.pay.controller;

import com.zhe.mall.common.result.Result;
import com.zhe.mall.pay.dto.CallbackRequest;
import com.zhe.mall.pay.dto.CallbackResultVO;
import com.zhe.mall.pay.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道回调入口（28.4 / ADR-021）。
 *
 * <p>这条路径在网关白名单内（{@code POST:/api/pay/callback/**}）：渠道侧没有用户令牌，白名单是唯一可行入口。
 * 因此安全<b>完全</b>由「验签 + 时间窗 + 回调流水唯一索引」保证——三个都在
 * {@link PaymentService#handleCallback} 里，且顺序不可调换。
 *
 * <p>3 条纪律（28.6）：
 * <ol>
 *   <li>先落流水再做业务；</li>
 *   <li>不信任外部传入的状态映射，只认 SUCCESS / FAIL；</li>
 *   <li>必须返回 200 且 body 为统一响应，否则渠道会重试风暴。</li>
 * </ol>
 * 本 Controller 不 try/catch：异常交给 {@code GlobalExceptionHandler} 统一转成统一响应体，
 * 而留痕已经在 Service 内落库（不依赖事务，见 PaymentService 类注释）。
 */
@RestController
@RequestMapping("/api/pay/callback")
public class PayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PayCallbackController.class);

    private final PaymentService paymentService;

    public PayCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 渠道异步回调：路径上的 {channel} 决定用哪个通道的密钥验签。 */
    @PostMapping("/{channel}")
    public Result<CallbackResultVO> callback(@PathVariable("channel") String channel,
                                             @RequestBody CallbackRequest request,
                                             HttpServletRequest servletRequest) {
        String remoteIp = resolveRemoteIp(servletRequest);
        log.info("收到渠道回调: channel={}, paymentNo={}, channelTradeNo={}, remoteIp={}",
                channel, request.paymentNo(), request.channelTradeNo(), remoteIp);
        return Result.success(paymentService.handleCallback(channel, request, remoteIp));
    }

    /**
     * 来源 IP：优先取 {@code X-Forwarded-For} 第一段（网关/代理场景），否则取直连地址。
     *
     * <p>仅用于审计与排障，不作为安全判断依据——真实通道还会额外校验来源 IP 白名单（ADR-021）。
     */
    private String resolveRemoteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
