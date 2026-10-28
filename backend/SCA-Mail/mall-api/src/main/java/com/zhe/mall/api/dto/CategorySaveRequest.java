package com.zhe.mall.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存分类入参（新增与编辑共用），见 M3 计划「后端 · mall-product（分类/品牌 CRUD）」。
 *
 * <p>放在 mall-api：BFF 需要把同一份请求体透传给 mall-product，Feign 请求体类型必须两端共享一份。
 *
 * <p>{@code level} 不在这里：它由服务端依据父分类推导（{@code parentId = 0} → 1，否则 {@code 父.level + 1}），
 * 让客户端传 level 会给出"父子层级对不上"的可乘之机。
 */
public record CategorySaveRequest(
        Long parentId,
        @NotBlank(message = "分类名称不能为空") @Size(max = 64, message = "分类名称过长") String name,
        Integer sortNo,
        Integer status
) {
}