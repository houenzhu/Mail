package com.zhe.mall.product.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.common.cache.MallCache;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.product.dto.CProductDetailVO;
import com.zhe.mall.product.dto.ProductDetailVO;
import com.zhe.mall.product.mapper.BrandMapper;
import com.zhe.mall.product.mapper.CategoryMapper;
import com.zhe.mall.product.mapper.ProductMapper;
import com.zhe.mall.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C 端商品详情：库存必须实时查询，且不进入商品详情缓存。 */
class ProductServiceCDetailTest {

    private static final Long PRODUCT_ID = 1L;

    private static final Long SKU_ID = 1001L;

    private InventoryApi inventoryApi;

    private ProductService service;

    @BeforeEach
    void setUp() {
        inventoryApi = mock(InventoryApi.class);
        MallCache mallCache = mock(MallCache.class);
        when(mallCache.getOrLoad(anyString(), eq(ProductDetailVO.class), any()))
                .thenReturn(new ProductDetailVO(product(1), List.of(sku())));
        service = new ProductService(mock(ProductMapper.class), mock(ProductSkuMapper.class),
                mock(CategoryMapper.class), mock(BrandMapper.class), inventoryApi, mallCache);
    }

    @Test
    @DisplayName("商品详情实时补可售库存，且库存不进入缓存")
    void fillsAvailableStockAtReadTime() {
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.success(Map.of(SKU_ID, 7)));

        CProductDetailVO detail = service.cDetail(PRODUCT_ID);

        assertThat(detail.skus()).hasSize(1);
        assertThat(detail.skus().get(0).available()).isEqualTo(7);
    }

    @Test
    @DisplayName("库存服务不可用时库存为 null（未知），商品基础信息仍可展示")
    void degradesToUnknownWhenInventoryUnavailable() {
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE));

        CProductDetailVO detail = service.cDetail(PRODUCT_ID);

        assertThat(detail.skus().get(0).available()).isNull();
    }

    @Test
    @DisplayName("已下架商品详情返回业务错误")
    void rejectsOffShelfProduct() {
        MallCache mallCache = mock(MallCache.class);
        when(mallCache.getOrLoad(anyString(), eq(ProductDetailVO.class), any()))
                .thenReturn(new ProductDetailVO(product(0), List.of(sku())));
        service = new ProductService(mock(ProductMapper.class), mock(ProductSkuMapper.class),
                mock(CategoryMapper.class), mock(BrandMapper.class), inventoryApi, mallCache);

        assertThatThrownBy(() -> service.cDetail(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_OFF_SHELF.getCode());
    }

    private static ProductDTO product(int status) {
        return new ProductDTO(PRODUCT_ID, "无线降噪耳机 Pro", "主动降噪", "https://img.example.com/p.png",
                new BigDecimal("499.00"), new BigDecimal("499.00"), status, 1L, 1L, 100,
                "2026-11-01 10:00:00", "2026-11-01 10:00:00");
    }

    private static SkuDTO sku() {
        return new SkuDTO(SKU_ID, PRODUCT_ID, "SKU-1001", "黑色 / 标准版", "{\"颜色\":\"黑色\"}",
                new BigDecimal("499.00"), new BigDecimal("599.00"), "https://img.example.com/sku.png", 1);
    }
}
