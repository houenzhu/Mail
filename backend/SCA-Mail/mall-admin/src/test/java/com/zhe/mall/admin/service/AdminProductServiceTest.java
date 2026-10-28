package com.zhe.mall.admin.service;

import com.zhe.mall.admin.dto.AdminProductPage;
import com.zhe.mall.admin.dto.AdminProductRow;
import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.ProductApi;
import com.zhe.mall.api.dto.AdminProductPageQuery;
import com.zhe.mall.api.dto.AdminProductStatusCounts;
import com.zhe.mall.api.dto.BrandDTO;
import com.zhe.mall.api.dto.BrandSaveRequest;
import com.zhe.mall.api.dto.CategoryDTO;
import com.zhe.mall.api.dto.CategorySaveRequest;
import com.zhe.mall.api.dto.DeductRequest;
import com.zhe.mall.api.dto.DeductResult;
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AdminProductService} 的聚合逻辑测试。
 *
 * <p>这是后台商品列表最核心、也最容易写错的一段：商品与库存分属两个库（禁止跨库 JOIN），
 * 只能由 BFF 合并（见计划文档 8.2）。因此这里逐条验证：
 * <ul>
 *   <li>跨服务字段确实被合并进每一行（库存求和、SKU 数量、分类/品牌名）；</li>
 *   <li><b>批量调用只发生一次</b>——用调用计数断言，防止以后被改成逐商品查询（N+1 回归）；</li>
 *   <li>附加数据失败时降级（列表仍可看）而主数据失败时上抛（不伪装成"没有商品"）。</li>
 * </ul>
 *
 * <p>用**手写桩**而不是 Mockito：接口方法少、需要记录的调用次数很明确，
 * 手写更直观，也不给工程增加测试依赖。
 */
class AdminProductServiceTest {

    private static final long PAGE_TOTAL = 2L;

    @Test
    @DisplayName("按商品批量聚合：库存求和、SKU 数量、分类与品牌名都要落到行上")
    void aggregatesInventorySkuAndDictionaryFields() {
        StubProductApi productApi = new StubProductApi();
        StubInventoryApi inventoryApi = new StubInventoryApi();
        AdminProductService service = new AdminProductService(productApi, inventoryApi);

        AdminProductPage page = service.page(new AdminProductPageQuery(null, null, null, null, null, 0, 1, 10));

        assertThat(page.total()).isEqualTo(PAGE_TOTAL);
        assertThat(page.records()).hasSize(2);

        AdminProductRow first = page.records().get(0);
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.categoryName()).isEqualTo("手机数码");
        assertThat(first.brandName()).isEqualTo("华为");
        assertThat(first.skuCount()).isEqualTo(2);
        // SKU 1001(available 93) + 1002(available 49) = 142
        assertThat(first.stock()).isEqualTo(142);
        assertThat(first.sales()).isEqualTo(120);
        assertThat(first.createdAt()).isEqualTo("2026-09-01 10:00:00");

        // 第二个商品只有 1 个 SKU，库存 7
        AdminProductRow second = page.records().get(1);
        assertThat(second.skuCount()).isEqualTo(1);
        assertThat(second.stock()).isEqualTo(7);
        // brandId=0 表示无品牌，不应拿去字典里查出名字
        assertThat(second.brandId()).isNull();
        assertThat(second.brandName()).isNull();
    }

    @Test
    @DisplayName("整页只发一次批量 SKU 与一次批量库存调用（防止 N+1 回归）")
    void usesBatchCallsInsteadOfPerRowQueries() {
        StubProductApi productApi = new StubProductApi();
        StubInventoryApi inventoryApi = new StubInventoryApi();
        AdminProductService service = new AdminProductService(productApi, inventoryApi);

        service.page(new AdminProductPageQuery(null, null, null, null, null, 0, 1, 10));

        assertThat(productApi.skuBatchCalls).isEqualTo(1);
        assertThat(inventoryApi.batchCalls).isEqualTo(1);
        // 商品分页与状态计数各一次
        assertThat(productApi.pageCalls).isEqualTo(1);
        assertThat(productApi.statusCountCalls).isEqualTo(1);
        // 批量库存请求里应带上整页的 SKU（3 个），而不是每行各查一次
        assertThat(inventoryApi.lastSkuIds).containsExactly(1001L, 1002L, 1003L);
    }

    @Test
    @DisplayName("库存服务不可用：降级为空库存，但商品列表仍然返回（不整页失败）")
    void degradesWhenInventoryUnavailable() {
        StubProductApi productApi = new StubProductApi();
        StubInventoryApi inventoryApi = new StubInventoryApi();
        inventoryApi.failBatch = true;
        AdminProductService service = new AdminProductService(productApi, inventoryApi);

        AdminProductPage page = service.page(new AdminProductPageQuery(null, null, null, null, null, 0, 1, 10));

        assertThat(page.records()).hasSize(2);
        assertThat(page.records().get(0).stock()).isNull();
        // SKU 数量来自商品侧，仍应可用
        assertThat(page.records().get(0).skuCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("商品分页失败：必须上抛（否则会伪装成「没有商品」）")
    void propagatesFailureOfProductPage() {
        StubProductApi productApi = new StubProductApi();
        productApi.failPage = true;
        AdminProductService service = new AdminProductService(productApi, new StubInventoryApi());

        assertThatThrownBy(() -> service.page(new AdminProductPageQuery(null, null, null, null, null, 0, 1, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询商品分页失败");
    }

    @Test
    @DisplayName("空页：不发起下游聚合调用，直接返回空")
    void skipsAggregationOnEmptyPage() {
        StubProductApi productApi = new StubProductApi();
        productApi.emptyPage = true;
        StubInventoryApi inventoryApi = new StubInventoryApi();
        AdminProductService service = new AdminProductService(productApi, inventoryApi);

        AdminProductPage page = service.page(new AdminProductPageQuery(null, null, null, null, null, 0, 1, 10));

        assertThat(page.records()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(productApi.skuBatchCalls).isZero();
        assertThat(inventoryApi.batchCalls).isZero();
    }

    /* ---------------- 手写桩 ---------------- */

    private static ProductDTO product(long id, String name, Long categoryId, Long brandId, int sales) {
        return new ProductDTO(id, name, name + " 卖点", null,
                new BigDecimal("499.00"), new BigDecimal("519.00"), 1,
                categoryId, brandId, sales, "2026-09-01 10:00:00", "2026-09-02 10:00:00");
    }

    private static class StubProductApi implements ProductApi {

        int pageCalls;
        int statusCountCalls;
        int skuBatchCalls;
        boolean failPage;
        boolean emptyPage;

        @Override
        public Result<PageResult<ProductDTO>> adminPage(AdminProductPageQuery query) {
            pageCalls++;
            if (failPage) {
                return Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "mall-product 不可用");
            }
            if (emptyPage) {
                return Result.success(PageResult.empty(1, 10));
            }
            List<ProductDTO> rows = List.of(
                    product(1L, "无线降噪耳机 Pro", 1L, 1L, 120),
                    product(2L, "纯棉圆领T恤", 2L, 0L, 35));
            return Result.success(PageResult.of(PAGE_TOTAL, 1, 10, rows));
        }

        @Override
        public Result<AdminProductStatusCounts> adminStatusCounts() {
            statusCountCalls++;
            return Result.success(new AdminProductStatusCounts(2L, 2L, 0L, 0L));
        }

        @Override
        public Result<List<CategoryDTO>> listCategories() {
            return Result.success(List.of(
                    new CategoryDTO(1L, 0L, "手机数码", 1, 1, 1),
                    new CategoryDTO(2L, 0L, "服饰鞋包", 1, 2, 1)));
        }

        @Override
        public Result<List<BrandDTO>> listBrands() {
            return Result.success(List.of(new BrandDTO(1L, "华为", null, 1, 1)));
        }

        @Override
        public Result<Map<Long, List<SkuDTO>>> adminSkuBatch(List<Long> productIds) {
            skuBatchCalls++;
            Map<Long, List<SkuDTO>> map = new LinkedHashMap<>();
            map.put(1L, List.of(
                    new SkuDTO(1001L, 1L, "SKU-1001", "默认规格", null, new BigDecimal("499.00"), new BigDecimal("519.00"), null, 1),
                    new SkuDTO(1002L, 1L, "SKU-1002", "套装版", null, new BigDecimal("519.00"), new BigDecimal("599.00"), null, 1)));
            map.put(2L, List.of(
                    new SkuDTO(1003L, 2L, "SKU-1003", "默认规格", null, new BigDecimal("79.00"), new BigDecimal("99.00"), null, 1)));
            return Result.success(map);
        }

        @Override
        public Result<ProductDTO> getById(Long productId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<SkuDTO> getSkuById(Long skuId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> updateStatus(Long productId, Integer status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> softDelete(Long productId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<List<SkuDTO>> skuByIds(List<Long> skuIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Long> create(ProductSaveRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> update(Long productId, ProductSaveRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Long> createCategory(CategorySaveRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> updateCategory(Long categoryId, CategorySaveRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> deleteCategory(Long categoryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Long> createBrand(BrandSaveRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> updateBrand(Long brandId, BrandSaveRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> deleteBrand(Long brandId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class StubInventoryApi implements InventoryApi {

        int batchCalls;
        List<Long> lastSkuIds = new ArrayList<>();
        boolean failBatch;

        @Override
        public Result<Map<Long, InventoryDTO>> getBatch(InventoryBatchRequest request) {
            batchCalls++;
            lastSkuIds = request == null || request.skuIds() == null ? List.of() : List.copyOf(request.skuIds());
            if (failBatch) {
                return Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "mall-inventory 不可用");
            }
            Map<Long, InventoryDTO> map = new LinkedHashMap<>();
            map.put(1001L, new InventoryDTO(1001L, 100, 7, 93));
            map.put(1002L, new InventoryDTO(1002L, 50, 1, 49));
            map.put(1003L, new InventoryDTO(1003L, 200, 193, 7));
            return Result.success(map);
        }

        @Override
        public Result<InventoryDTO> getBySkuId(Long skuId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<DeductResult> deduct(DeductRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<DeductResult> rollback(DeductRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> init(InitStockRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
