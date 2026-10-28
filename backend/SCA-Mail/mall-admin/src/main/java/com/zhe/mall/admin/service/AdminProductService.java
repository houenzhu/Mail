package com.zhe.mall.admin.service;

import com.zhe.mall.admin.dto.AdminProductDetail;
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
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.InventoryDTO;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理端商品聚合服务。
 *
 * <p>聚合两类数据（见计划文档 8.2）：
 * <ol>
 *   <li><b>商品域</b>：mall-product 提供分页、分类、品牌；</li>
 *   <li><b>库存域</b>：mall-inventory 提供库存，二者是独立库，禁止跨库 JOIN，
 *       所以由 BFF 用**批量接口**合并（一次查询拿整页库存，不做逐行 N+1）。</li>
 * </ol>
 *
 * <p><b>降级口径</b>：库存/分类/品牌属于"锦上添花"的附加数据，下游不可用时**不阻断列表**，
 * 只把对应字段留空并打 WARN——后台列表看得到商品，比"整页 500"有用得多。
 * 而商品分页本身失败则必须上抛，否则会伪装成"没有商品"。
 */
@Service
public class AdminProductService {

    private static final Logger log = LoggerFactory.getLogger(AdminProductService.class);

    private final ProductApi productApi;

    private final InventoryApi inventoryApi;

    public AdminProductService(ProductApi productApi, InventoryApi inventoryApi) {
        this.productApi = productApi;
        this.inventoryApi = inventoryApi;
    }

    public AdminProductPage page(AdminProductPageQuery query) {
        PageResult<ProductDTO> page = unwrap(productApi.adminPage(query), "查询商品分页");
        List<ProductDTO> products = page.list() == null ? List.of() : page.list();

        Map<Long, String> categoryNames = categoryNameMap();
        Map<Long, String> brandNames = brandNameMap();

        // 两次批量调用拿到整页数据，避免逐商品/逐 SKU 查询（N+1）：
        // 商品在 mall_product、库存在 mall_inventory，跨库不能 JOIN，只能由 BFF 合并（见计划文档 8.2）。
        Map<Long, List<SkuDTO>> productSkus = skusByProduct(products);
        Map<Long, Integer> stocks = stockByProduct(products, productSkus);

        List<AdminProductRow> records = products.stream()
                .map(product -> {
                    List<SkuDTO> skus = productSkus.getOrDefault(product.id(), List.of());
                    return new AdminProductRow(
                            product.id(),
                            product.name(),
                            product.subtitle(),
                            product.mainImage(),
                            product.categoryId(),
                            categoryNames.get(product.categoryId()),
                            product.brandId() == null || product.brandId() == 0 ? null : product.brandId(),
                            brandNames.get(product.brandId()),
                            product.priceMin(),
                            product.priceMax(),
                            product.status(),
                            query.deleted() == null ? 0 : query.deleted(),
                            skus.size(),
                            stocks.get(product.id()),
                            product.sales(),
                            // 30 日销量需要按时间窗口聚合订单明细，订单在 mall_order（另一个库），
                            // 需 mall-order 提供接口后补齐（见计划文档 G-13）。当前明确留空，
                            // 不用累计销量冒充，避免给出错误口径。
                            null,
                            product.createdAt(),
                            product.updatedAt());
                })
                .toList();

        return new AdminProductPage(
                page.total(), page.pageNo(), page.pageSize(), records, counts());
    }

    public AdminProductPage.Counts counts() {
        AdminProductStatusCounts counts = unwrap(productApi.adminStatusCounts(), "查询商品状态计数");
        return new AdminProductPage.Counts(counts.all(), counts.onSale(), counts.offShelf(), counts.deleted());
    }

    /* ---------------- 目录（分类 / 品牌） ---------------- */

    /**
     * 分类列表（发布商品的必填下拉）。
     *
     * <p>与 {@link #page} 里的分类名映射不同：这里是下拉框的**主数据**，
     * 拿不到就没有可选项，因此不降级——失败直接上抛，避免页面显示"没有分类"。
     */
    public List<CategoryDTO> listCategories() {
        return unwrap(productApi.listCategories(), "查询分类");
    }

    /** 品牌列表（同上，主数据不降级）。 */
    public List<BrandDTO> listBrands() {
        return unwrap(productApi.listBrands(), "查询品牌");
    }

    /* ---------------- 商品写操作 ---------------- */

    /**
     * 单商品 SKU 列表（编辑回显用）。
     *
     * <p>复用批量接口而不是让下游再开一条单查：批量接口对 1 个 ID 同样成立，
     * 少一条 SQL、少一处需要同步维护的查询。
     */
    public List<SkuDTO> skus(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的商品 ID: " + productId);
        }
        Map<Long, List<SkuDTO>> grouped = unwrap(productApi.adminSkuBatch(List.of(productId)), "查询 SKU");
        return grouped == null ? List.of() : grouped.getOrDefault(productId, List.of());
    }

    /** 上下架：透传给 mall-product，角色校验在下游的 {@code @RequireStaff(OPERATOR)}。 */
    public void updateStatus(Long productId, Integer status) {
        unwrap(productApi.updateStatus(productId, status), "更新商品状态");
    }

    /** 逻辑删除：透传给 mall-product，角色校验在下游的 {@code @RequireStaff(OPERATOR)}。 */
    public void delete(Long productId) {
        unwrap(productApi.softDelete(productId), "删除商品");
    }

    /**
     * 商品详情（编辑回显）：商品主体 + 真实 SKU 列表。
     *
     * <p>此前编辑页是"假回显"——用分页接口按 ID 过滤后自己编一个 SKU（见 M3 计划 Context）。
     * 这里改为一次拿回真实 SKU（含 skuId / 划线价），前端才能做差量提交。
     *
     * <p>两个调用都是主数据，失败直接上抛：宁可报错，也不要给出一份"SKU 为空"的假表单——
     * 用户一旦在假表单上点保存，就会把真实 SKU 全部软删（差量规则里"请求未出现的即删除"）。
     */
    public AdminProductDetail detail(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的商品 ID: " + productId);
        }
        ProductDTO product = unwrap(productApi.getById(productId), "查询商品");
        return new AdminProductDetail(product, skus(productId));
    }

    /** 新增商品：透传给 mall-product（含 SKU 与库存初始化），角色校验在下游。 */
    public Long create(ProductSaveRequest request) {
        return unwrap(productApi.create(request), "新增商品");
    }

    /** 编辑商品：透传给 mall-product（SKU 差量更新），角色校验在下游。 */
    public void update(Long productId, ProductSaveRequest request) {
        unwrap(productApi.update(productId, request), "编辑商品");
    }

    /* ---------------- 分类 / 品牌维护（写接口全部透传下游，角色校验在下游） ---------------- */

    public Long createCategory(CategorySaveRequest request) {
        return unwrap(productApi.createCategory(request), "新增分类");
    }

    public void updateCategory(Long categoryId, CategorySaveRequest request) {
        unwrap(productApi.updateCategory(categoryId, request), "编辑分类");
    }

    public void deleteCategory(Long categoryId) {
        unwrap(productApi.deleteCategory(categoryId), "删除分类");
    }

    public Long createBrand(BrandSaveRequest request) {
        return unwrap(productApi.createBrand(request), "新增品牌");
    }

    public void updateBrand(Long brandId, BrandSaveRequest request) {
        unwrap(productApi.updateBrand(brandId, request), "编辑品牌");
    }

    public void deleteBrand(Long brandId) {
        unwrap(productApi.deleteBrand(brandId), "删除品牌");
    }

    private Map<Long, String> categoryNameMap() {
        try {
            List<CategoryDTO> categories = unwrap(productApi.listCategories(), "查询分类");
            return categories.stream()
                    .collect(Collectors.toMap(CategoryDTO::id, CategoryDTO::name, (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("分类列表获取失败，商品列表的分类名将为空: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, String> brandNameMap() {
        try {
            List<BrandDTO> brands = unwrap(productApi.listBrands(), "查询品牌");
            return brands.stream()
                    .collect(Collectors.toMap(BrandDTO::id, BrandDTO::name, (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("品牌列表获取失败，商品列表的品牌名将为空: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 整页商品的 SKU（一次批量查询）。
     *
     * <p>附加数据：失败只降级（SKU 数量与库存列为空），不阻断商品列表本身。
     */
    private Map<Long, List<SkuDTO>> skusByProduct(List<ProductDTO> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        try {
            List<Long> productIds = products.stream().map(ProductDTO::id).toList();
            Map<Long, List<SkuDTO>> result = unwrap(productApi.adminSkuBatch(productIds), "批量查询 SKU");
            return result == null ? Map.of() : result;
        } catch (RuntimeException e) {
            log.warn("批量查询 SKU 失败，SKU 数量与库存列将为空: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 整页商品的库存：商品行展示其各 SKU 可售库存之和。
     *
     * <p>用 mall-inventory 的批量接口一次查完（skus 已在上一步批量拿到），
     * 因此整页只有 2 次下游调用，与行数无关。
     */
    private Map<Long, Integer> stockByProduct(List<ProductDTO> products, Map<Long, List<SkuDTO>> skus) {
        if (products.isEmpty() || skus.isEmpty()) {
            return Map.of();
        }
        List<Long> skuIds = skus.values().stream()
                .flatMap(List::stream)
                .map(SkuDTO::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, InventoryDTO> inventory;
        try {
            inventory = batchInventory(skuIds);
        } catch (RuntimeException e) {
            log.warn("批量查询库存失败，商品列表的库存列将为空: {}", e.getMessage());
            return Map.of();
        }

        Map<Long, Integer> stockByProduct = new HashMap<>();
        for (Map.Entry<Long, List<SkuDTO>> entry : skus.entrySet()) {
            int available = 0;
            for (SkuDTO sku : entry.getValue()) {
                InventoryDTO dto = inventory.get(sku.id());
                if (dto != null && dto.available() != null) {
                    available += dto.available();
                }
            }
            stockByProduct.put(entry.getKey(), available);
        }
        return stockByProduct;
    }

    /** 取 Result.data，业务失败转成异常（BFF 不掩盖下游的业务错误码）。 */
    private <T> T unwrap(Result<T> result, String action) {
        if (result == null || result.code() != ResultCode.SUCCESS.getCode()) {
            String message = result == null ? "下游无响应" : result.message();
            log.warn("{}失败: code={}, message={}", action,
                    result == null ? null : result.code(), message);
            throw new BusinessException(
                    result == null ? ResultCode.DOWNSTREAM_UNAVAILABLE.getCode() : result.code(),
                    action + "失败：" + message);
        }
        return result.data();
    }

    /** 批量查询库存（mall-inventory）。 */
    private Map<Long, InventoryDTO> batchInventory(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InventoryDTO> result =
                unwrap(inventoryApi.getBatch(new InventoryBatchRequest(skuIds)), "批量查询库存");
        return result == null ? Map.of() : result;
    }
}
