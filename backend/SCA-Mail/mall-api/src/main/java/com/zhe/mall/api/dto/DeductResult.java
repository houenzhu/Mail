package com.zhe.mall.api.dto;

import com.zhe.mall.common.result.ResultCode;

/**
 * 库存扣减 / 回滚结果。业务失败通过 {@code code} 表达具体原因（见需求文档 18.3），
 * 调用方据此抛出对应错误码，避免把"库存不足"笼统报成"下游不可用"。
 */
public record DeductResult(boolean success, int code, String message, Integer remaining) {

    public static DeductResult ok(Integer remaining) {
        return new DeductResult(true, ResultCode.SUCCESS.getCode(), "扣减成功", remaining);
    }

    public static DeductResult rollbackOk(Integer remaining) {
        return new DeductResult(true, ResultCode.SUCCESS.getCode(), "回滚成功", remaining);
    }

    public static DeductResult fail(ResultCode resultCode, String message) {
        return new DeductResult(false, resultCode.getCode(), message, null);
    }
}
