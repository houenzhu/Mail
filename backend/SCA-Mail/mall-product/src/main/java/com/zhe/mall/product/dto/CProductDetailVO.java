package com.zhe.mall.product.dto;

import com.zhe.mall.api.dto.ProductDTO;

import java.util.List;

/** C 端商品详情：SPU + SKU（含实时可售库存）。 */
public record CProductDetailVO(ProductDTO product, List<CProductSkuVO> skus) {
}
