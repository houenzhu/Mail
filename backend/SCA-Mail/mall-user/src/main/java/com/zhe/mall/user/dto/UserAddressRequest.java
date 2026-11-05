package com.zhe.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 地址新增 / 修改入参。
 *
 * <p>长度上限与 DDL 列宽一致：超长必须在这里拦成 10001，
 * 否则会落到 MySQL 层抛 DataTooLong，前端只能看到 500。
 */
public record UserAddressRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 64, message = "收货人不能超过 64 个字符") String receiverName,

        @NotBlank(message = "收货电话不能为空")
        @Size(max = 20, message = "收货电话不能超过 20 个字符") String receiverPhone,

        @Size(max = 120, message = "地区不能超过 120 个字符") String region,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址不能超过 255 个字符") String detailAddress,

        Boolean isDefault) {

    public boolean defaultRequested() {
        return Boolean.TRUE.equals(isDefault);
    }
}
