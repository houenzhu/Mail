package com.zhe.mall.pay.enums;

/**
 * 对账差异分类（28.8），落库为 {@code pay_reconcile_diff.diff_type}。
 */
public enum ReconcileDiffType {

    /** 渠道有、库中无：可能回调丢失或串单 */
    CHANNEL_ONLY,

    /** 库中有、渠道无：虚假支付，必须人工介入 */
    LOCAL_ONLY,

    /** 金额不一致 */
    AMOUNT_MISMATCH,

    /** 状态不一致 */
    STATUS_MISMATCH
}
