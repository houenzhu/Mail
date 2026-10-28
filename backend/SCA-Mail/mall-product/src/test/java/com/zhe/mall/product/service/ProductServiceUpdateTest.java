package com.zhe.mall.product.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuSaveRequest;
import com.zhe.mall.common.cache.MallCache;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.product.entity.Product;
import com.zhe.mall.product.entity.ProductSku;
import com.zhe.mall.product.mapper.BrandMapper;
import com.zhe.mall.product.mapper.CategoryMapper;
import com.zhe.mall.product.mapper.ProductMapper;
import com.zhe.mall.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductService#update(Long, ProductSaveRequest)} 的 SKU 差量规则测试。
 *
 * <p>这段逻辑是 M3 的核心风险点：{@code inventory.sku_id} 与订单明细都引用 SKU 主键，
 * 一旦写成"整表重建"，库存与历史订单就会全部对不上（见 M3 计划「决策 2」）。
 * 因此逐条锁定：**已有 SKU 原地更新（主键不变）、新 SKU 插入并初始化库存、缺失 SKU 软删**。
 *
 * <p>这里用 Mockito 而不是手写桩：Mapper 接口方法较多（含统计类查询），
 * 手写桩会把测试淹没在几十个 {@code throw new UnsupportedOperationException()} 里，
 * 反而看不清被测规则。
 */
class ProductServiceUpdateTest {

    private static final Long PRODUCT_ID = 7L;

    private ProductMapper productMapper;

    private ProductSkuMapper productSkuMapper;

    private InventoryApi inventoryApi;

    private ProductService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        productSkuMapper = mock(ProductSkuMapper.class);
        inventoryApi = mock(InventoryApi.class);
        service = new ProductService(productMapper, productSkuMapper,
                mock(CategoryMapper.class), mock(BrandMapper.class), inventoryApi, mock(MallCache.class));

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setStatus(1);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(inventoryApi.init(any(InitStockRequest.class))).thenReturn(Result.success());
    }

    @Test
    @DisplayName("已有 SKU 原地更新（主键不变），新 SKU 插入并初始化库存，缺失 SKU 被软删")
    void appliesSkuDiffInsteadOfRebuilding() {
        // 库中：101（保留）、102（请求里没有 → 应被软删）
        when(productSkuMapper.selectByProductId(PRODUCT_ID)).thenReturn(List.of(sku(101L, "SKU-A"), sku(102L, "SKU-B")));
        when(productSkuMapper.countBySkuCodeExcluding(any(), any())).thenReturn(0);

        service.update(PRODUCT_ID, request(
                new SkuSaveRequest(101L, "SKU-A", "默认规格", null, new BigDecimal("88.00"), null, null, null),
                new SkuSaveRequest(null, "SKU-C", "新增规格", null, new BigDecimal("120.00"), null, null, 30)));

        // 1) 已有 SKU 走 updateBatch，且带的就是原主键 101
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSku>> updated = ArgumentCaptor.forClass(List.class);
        verify(productSkuMapper).updateBatch(updated.capture());
        assertThat(updated.getValue()).hasSize(1);
        assertThat(updated.getValue().get(0).getId()).isEqualTo(101L);
        assertThat(updated.getValue().get(0).getPrice()).isEqualByComparingTo("88.00");

        // 2) 新 SKU 走 insertBatch（未设置主键 → 由数据库生成）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSku>> inserted = ArgumentCaptor.forClass(List.class);
        verify(productSkuMapper).insertBatch(inserted.capture());
        assertThat(inserted.getValue()).hasSize(1);
        assertThat(inserted.getValue().get(0).getId()).isNull();
        assertThat(inserted.getValue().get(0).getSkuCode()).isEqualTo("SKU-C");

        // 3) 请求里缺失的 102 被软删；库存初始化只对新增 SKU 发生
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> deleted = ArgumentCaptor.forClass(List.class);
        verify(productSkuMapper).softDeleteByIds(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(102L);
        // insertBatch 未回填主键（桩没模拟），因此这里的 skuId 仍是 null；用 anyLong 之外的宽松断言
        verify(inventoryApi, times(1)).init(any(InitStockRequest.class));
    }

    @Test
    @DisplayName("已有 SKU 未改编码时不得误报「编码已存在」：查重要排除自身")
    void excludesSelfWhenCheckingSkuCode() {
        when(productSkuMapper.selectByProductId(PRODUCT_ID)).thenReturn(List.of(sku(101L, "SKU-A")));
        when(productSkuMapper.countBySkuCodeExcluding(any(), any())).thenReturn(0);

        service.update(PRODUCT_ID, request(
                new SkuSaveRequest(101L, "SKU-A", "默认规格", null, new BigDecimal("88.00"), null, null, null)));

        verify(productSkuMapper).countBySkuCodeExcluding("SKU-A", 101L);
        verify(productSkuMapper).updateBatch(anyList());
        verify(productSkuMapper, never()).softDeleteByIds(anyList());
    }

    @Test
    @DisplayName("新 SKU 编码与他人重复：直接拒绝（33004 之外的 32004）")
    void rejectsDuplicatedNewSkuCode() {
        when(productSkuMapper.selectByProductId(PRODUCT_ID)).thenReturn(List.of(sku(101L, "SKU-A")));
        when(productSkuMapper.countBySkuCodeExcluding(eq("SKU-DUP"), isNull())).thenReturn(1);

        assertThatThrownBy(() -> service.update(PRODUCT_ID, request(
                new SkuSaveRequest(101L, "SKU-A", "默认规格", null, new BigDecimal("88.00"), null, null, null),
                new SkuSaveRequest(null, "SKU-DUP", "重复编码", null, new BigDecimal("9.00"), null, null, null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SKU_CODE_DUPLICATED.getCode());
    }

    @Test
    @DisplayName("提交了不属于本商品的 skuId：拒绝，避免改到别人的 SKU")
    void rejectsForeignKeySkuId() {
        when(productSkuMapper.selectByProductId(PRODUCT_ID)).thenReturn(List.of(sku(101L, "SKU-A")));

        assertThatThrownBy(() -> service.update(PRODUCT_ID, request(
                new SkuSaveRequest(999L, "SKU-X", "别家的", null, new BigDecimal("1.00"), null, null, null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SKU_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("编辑后按请求里的全部 SKU 重算 price_min / price_max")
    void recalculatesPriceRange() {
        when(productSkuMapper.selectByProductId(PRODUCT_ID)).thenReturn(List.of(sku(101L, "SKU-A")));
        when(productSkuMapper.countBySkuCodeExcluding(any(), any())).thenReturn(0);

        service.update(PRODUCT_ID, request(
                new SkuSaveRequest(101L, "SKU-A", "默认规格", null, new BigDecimal("88.00"), null, null, null),
                new SkuSaveRequest(null, "SKU-C", "新增规格", null, new BigDecimal("120.00"), null, null, null)));

        verify(productMapper).updatePriceRange(eq(PRODUCT_ID),
                eq(new BigDecimal("88.00")), eq(new BigDecimal("120.00")));
    }

    @Test
    @DisplayName("同一次提交里出现重复编码：拦在写库之前")
    void rejectsDuplicatedCodeWithinRequest() {
        when(productSkuMapper.selectByProductId(PRODUCT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.update(PRODUCT_ID, request(
                new SkuSaveRequest(null, "SKU-SAME", "A", null, new BigDecimal("1.00"), null, null, null),
                new SkuSaveRequest(null, "SKU-SAME", "B", null, new BigDecimal("2.00"), null, null, null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SKU_CODE_DUPLICATED.getCode());
    }

    private static ProductSku sku(Long id, String code) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(PRODUCT_ID);
        sku.setSkuCode(code);
        sku.setName("默认规格");
        sku.setPrice(new BigDecimal("99.00"));
        return sku;
    }

    private static ProductSaveRequest request(SkuSaveRequest... skus) {
        return new ProductSaveRequest("测试商品", null, 1L, null, null, null, null, List.of(skus));
    }
}