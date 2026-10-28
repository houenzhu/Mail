package com.zhe.mall.pay.controller;

import com.zhe.mall.common.auth.AuthHeaders;
import com.zhe.mall.common.auth.InternalTokenVerifier;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.pay.dto.CallbackResultVO;
import com.zhe.mall.pay.dto.ReconcileResultVO;
import com.zhe.mall.pay.service.PayReconcileService;
import com.zhe.mall.pay.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 支付内部接口（28.4）：主动查询兜底与手工对账。
 *
 * <p>与订单侧同一套规则：网关拦截含 {@code /internal/} 的外部访问，服务端再校验
 * {@code X-Gateway-Token}；<b>先校验凭据再查业务</b>，避免变成探测接口。
 */
@RestController
@RequestMapping("/api/pay/internal")
public class PayInternalController {

    private final PaymentService paymentService;

    private final PayReconcileService payReconcileService;

    private final InternalTokenVerifier internalTokenVerifier;

    public PayInternalController(PaymentService paymentService,
                                 PayReconcileService payReconcileService,
                                 InternalTokenVerifier internalTokenVerifier) {
        this.paymentService = paymentService;
        this.payReconcileService = payReconcileService;
        this.internalTokenVerifier = internalTokenVerifier;
    }

    /** 主动查询渠道并对齐（回调丢失兜底 / 排障）。 */
    @PostMapping("/sync/{paymentNo}")
    public Result<CallbackResultVO> sync(@PathVariable("paymentNo") String paymentNo,
                                         @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false)
                                         String internalToken) {
        internalTokenVerifier.verify(internalToken);
        return Result.success(paymentService.sync(paymentNo));
    }

    /** 手工触发对账（{@code yyyy-MM-dd}），返回差异清单。 */
    @PostMapping("/reconcile/{bizDate}")
    public Result<ReconcileResultVO> reconcile(@PathVariable("bizDate") String bizDate,
                                               @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false)
                                               String internalToken) {
        internalTokenVerifier.verify(internalToken);
        LocalDate date;
        try {
            date = LocalDate.parse(bizDate);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "bizDate 格式非法，应为 yyyy-MM-dd: " + bizDate);
        }
        return Result.success(payReconcileService.reconcile(date));
    }
}
