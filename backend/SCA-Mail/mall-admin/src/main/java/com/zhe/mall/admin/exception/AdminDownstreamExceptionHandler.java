package com.zhe.mall.admin.exception;

import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 下游调用异常兜底：把 Feign 的 HTTP 状态码翻译回业务语义。
 *
 * <p><b>为什么需要</b>：mall-product 的角色校验 {@code @RequireStaff} 直接写
 * <em>HTTP 403 + code=20003</em>（见 RequireStaffInterceptor）。而 Feign 对非 2xx 一律抛
 * {@link FeignException}，不翻译就会被 mall-common 的兜底
 * {@code @ExceptionHandler(Exception.class)} 变成 <em>90001 系统异常</em>——
 * 把"你没有权限"谎报成"服务故障"，前端也就无法给出正确提示
 * （request.js 依赖 403 → 「无访问权限」）。
 *
 * <p>下游的<em>业务</em>失败是 HTTP 200 + 非零 code，由 {@code AdminProductService.unwrap}
 * 原样透传，不会走到这里；因此本类只需处理鉴权类（401/403）与依赖类（5xx）状态码。
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}：确保比 mall-common 的通用兜底处理器优先命中。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminDownstreamExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminDownstreamExceptionHandler.class);

    @ExceptionHandler(FeignException.class)
    public Result<Void> handleFeignException(FeignException exception) {
        int status = exception.status();
        ResultCode code;
        if (status == 401) {
            code = ResultCode.UNAUTHORIZED;
        } else if (status == 403) {
            code = ResultCode.FORBIDDEN;
        } else {
            code = ResultCode.DOWNSTREAM_UNAVAILABLE;
        }
        String path = exception.request() == null ? null : exception.request().url();
        log.warn("下游调用失败: status={}, path={}", status, path);
        return Result.failure(code, code.getMessage());
    }
}