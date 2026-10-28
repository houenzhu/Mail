package com.zhe.mall.order.statemachine;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.order.enums.OrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：所有流转集中在唯一入口校验，非法流转直接抛业务异常。
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED));
        // REFUNDING → PAID：渠道退款失败（驳回）后订单回到已支付，用户可再次申请（28.5）
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDING, EnumSet.of(OrderStatus.REFUNDED, OrderStatus.PAID));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransfer(OrderStatus from, OrderStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void check(OrderStatus from, OrderStatus to) {
        if (!canTransfer(from, to)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单状态不允许从 " + from.label() + " 流转到 " + to.label());
        }
    }
}
