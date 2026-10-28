package com.zhe.mall.product.dto;

import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.SkuDTO;

import java.util.List;

public record ProductDetailVO(ProductDTO product, List<SkuDTO> skus) {
}
