package com.zhe.mall.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存品牌入参（新增与编辑共用）。与 {@link CategorySaveRequest} 同样放在 mall-api 共享。
 */
public record BrandSaveRequest(
        @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称过长") String name,
        @Size(max = 255, message = "LOGO 地址过长") String logoUrl,
        @Size(max = 255, message = "品牌描述过长") String description,
        Integer sortNo,
        Integer status
) {
}