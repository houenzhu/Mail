package com.zhe.mall.admin.dto;

import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.SkuDTO;

import java.util.List;

/**
 * 管理端商品详情（编辑回显）：商品主体 + 真实 SKU 列表。
 *
 * <p>为什么要包一层而不是只返回 ProductDTO：编辑表单同时需要 SKU（差量提交要带 skuId），
 * 一次响应给全，前端就不必再补一次 SKU 请求（也就不会出现"两个请求各自失败"的半截表单）。
 */
public record AdminProductDetail(ProductDTO product, List<SkuDTO> skus) {
}