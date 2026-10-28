package com.zhe.mall.pay.support;

import com.zhe.mall.api.OrderApi;
import com.zhe.mall.api.dto.OrderPayView;
import com.zhe.mall.api.dto.PayCallbackRequest;
import com.zhe.mall.api.dto.RefundCallbackRequest;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单内部接口封装（28.10）：统一带内部令牌（由 {@code PayConfig} 的 Feign 拦截器注入）与错误映射。
 *
 * <p>两类方法刻意分开：
 * <ul>
 *   <li>{@link #getPayView} 是<b>同步查询</b>——失败必须让本次请求失败（否则会拿不到金额就建单）；</li>
 *   <li>{@link #notifyPaySuccess} / {@link #notifyRefund} 是<b>通知</b>——失败只记 ERROR，绝不回滚支付单状态。
 *       资金已经动了，订单状态滞后是"必须可对账"的问题，不是"必须回滚"的问题（28.5 失败兜底）。</li>
 * </ul>
 */
@Component
public class OrderClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(OrderClientWrapper.class);

    private final OrderApi orderApi;

    public OrderClientWrapper(OrderApi orderApi) {
        this.orderApi = orderApi;
    }

    /** 订单支付视图（严格模式）。 */
    public OrderPayView getPayView(String orderNo) {
        Result<OrderPayView> result;
        try {
            result = orderApi.getPayView(orderNo);
        } catch (Exception e) {
            log.error("调用订单服务失败: orderNo={}", orderNo, e);
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务不可用");
        }
        if (result == null) {
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务无响应");
        }
        if (!result.isSuccess() || result.data() == null) {
            // 透传下游错误码（如 34001 订单不存在 / 20003 凭据无效），便于排障时一眼定位
            throw new BusinessException(result.code(), "订单查询失败: " + result.message());
        }
        return result.data();
    }

    /** 通知订单"支付成功"。失败只记 ERROR，由查询任务 / 对账重试（订单侧回调幂等）。 */
    public boolean notifyPaySuccess(String orderNo, String paymentNo, Integer payType, java.math.BigDecimal amount) {
        try {
            Result<OrderPayView> result = orderApi.payCallback(orderNo,
                    new PayCallbackRequest(paymentNo, payType, amount));
            if (result != null && result.isSuccess()) {
                return true;
            }
            log.error("支付成功但订单回调失败（不回滚支付单，待查询任务/对账重试）: orderNo={}, paymentNo={}, code={}, message={}",
                    orderNo, paymentNo, result == null ? "无响应" : result.code(),
                    result == null ? "-" : result.message());
        } catch (Exception e) {
            log.error("支付成功但订单回调异常（不回滚支付单，待查询任务/对账重试）: orderNo={}, paymentNo={}",
                    orderNo, paymentNo, e);
        }
        return false;
    }

    /** 通知订单"退款阶段变化"。同上，失败只记 ERROR。 */
    public boolean notifyRefund(String orderNo, RefundCallbackRequest request) {
        try {
            Result<OrderPayView> result = orderApi.refundCallback(orderNo, request);
            if (result != null && result.isSuccess()) {
                return true;
            }
            log.error("退款回调订单失败（待对账重试）: orderNo={}, refundNo={}, stage={}, code={}, message={}",
                    orderNo, request.refundNo(), request.stage(),
                    result == null ? "无响应" : result.code(), result == null ? "-" : result.message());
        } catch (Exception e) {
            log.error("退款回调订单异常（待对账重试）: orderNo={}, refundNo={}, stage={}",
                    orderNo, request.refundNo(), request.stage(), e);
        }
        return false;
    }
}
