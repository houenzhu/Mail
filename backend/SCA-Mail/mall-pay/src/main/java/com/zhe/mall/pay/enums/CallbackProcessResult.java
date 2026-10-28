package com.zhe.mall.pay.enums;

/**
 * 回调流水的处理结果（28.6 表格最后一列），落库为 {@code payment_callback_log.process_result}。
 */
public final class CallbackProcessResult {

    public static final int SUCCESS = 1;

    public static final int SIGN_FAILED = 2;

    public static final int DUPLICATE_EVENT = 3;

    public static final int AMOUNT_MISMATCH = 4;

    public static final int STATUS_ILLEGAL = 5;

    public static final int RECONCILE_DIFF = 6;

    private CallbackProcessResult() {
    }
}
