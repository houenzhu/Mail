package com.zhe.mall.common.result;

/**
 * 统一错误码表（见需求文档 18.3）。
 *
 * <p>分段：0 成功；1xxxx 参数校验；2xxxx 认证授权；3xxxx 业务（30000 通用、31xxx 用户、
 * 32xxx 商品、33xxx 库存、34xxx 订单、35xxx 支付、36xxx 营销）；5xxxx 下游依赖；9xxxx 系统。
 */
public enum ResultCode {

    SUCCESS(0, "成功"),

    PARAM_ERROR(10001, "参数校验失败"),

    PARAM_MISSING(10002, "必填字段缺失"),

    PARAM_INVALID(10003, "字段格式非法"),

    UNAUTHORIZED(20001, "未登录或登录已过期"),

    TOKEN_EXPIRED(20002, "登录已过期，请重新登录"),

    FORBIDDEN(20003, "无访问权限"),

    RATE_LIMITED(40001, "请求过于频繁，请稍后重试"),

    BUSINESS_ERROR(30000, "业务处理失败"),

    USER_NOT_FOUND(31001, "用户不存在"),

    PHONE_REGISTERED(31002, "手机号已注册"),

    USERNAME_REGISTERED(31003, "用户名已存在"),

    PASSWORD_ERROR(31004, "账号或密码错误"),

    ACCOUNT_LOCKED(31005, "账号已被锁定，请稍后重试"),

    ACCOUNT_DISABLED(31006, "账号已注销或停用"),

    PRODUCT_NOT_FOUND(32001, "商品不存在"),

    PRODUCT_OFF_SHELF(32002, "商品已下架"),

    SKU_NOT_FOUND(32003, "SKU 不存在"),

    SKU_CODE_DUPLICATED(32004, "SKU 编码已存在"),

    CATEGORY_NOT_FOUND(32005, "分类不存在"),

    CATEGORY_NAME_DUPLICATED(32006, "同级分类名称已存在"),

    CATEGORY_IN_USE(32007, "分类下存在子分类或商品，无法删除"),

    BRAND_NOT_FOUND(32008, "品牌不存在"),

    BRAND_NAME_DUPLICATED(32009, "品牌名称已存在"),

    BRAND_IN_USE(32010, "品牌下存在商品，无法删除"),

    INVENTORY_NOT_FOUND(33001, "库存记录不存在"),

    STOCK_NOT_ENOUGH(33002, "库存不足"),

    DUPLICATE_REQUEST(33003, "重复请求处理中，请稍后重试"),

    ROLLBACK_FAILED(33004, "库存回滚失败"),

    ORDER_NOT_FOUND(34001, "订单不存在"),

    ORDER_STATUS_ILLEGAL(34002, "订单状态不允许该操作"),

    DUPLICATE_SUBMIT(34003, "重复提交"),

    PAY_AMOUNT_MISMATCH(34004, "回调金额与订单应付金额不一致"),

    PAYMENT_NOT_FOUND(35001, "支付单不存在"),

    SIGN_INVALID(35002, "签名校验失败"),

    DUPLICATE_CALLBACK(35003, "重复回调"),

    PAYMENT_STATUS_ILLEGAL(35004, "支付单状态不允许该操作"),

    REFUND_NOT_FOUND(35005, "退款单不存在"),

    REFUND_STATUS_ILLEGAL(35006, "退款状态不允许该操作"),

    PAY_CHANNEL_UNAVAILABLE(35007, "支付渠道不可用"),

    CALLBACK_EXPIRED(35008, "回调超出时间窗"),

    COUPON_NOT_FOUND(36001, "优惠券不存在或已领完"),

    COUPON_UNAVAILABLE(36002, "优惠券不可用"),

    SECKILL_ENDED(36003, "秒杀活动已结束"),

    DOWNSTREAM_UNAVAILABLE(50001, "下游服务不可用"),

    DOWNSTREAM_TIMEOUT(50002, "下游服务超时"),

    SYSTEM_ERROR(90001, "系统异常，请稍后重试");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
