package com.zhe.mall.api;

import com.zhe.mall.api.dto.AdminProductPageQuery;
import com.zhe.mall.api.dto.AdminProductStatusCounts;
import com.zhe.mall.api.dto.BrandDTO;
import com.zhe.mall.api.dto.BrandSaveRequest;
import com.zhe.mall.api.dto.CategoryDTO;
import com.zhe.mall.api.dto.CategorySaveRequest;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mall-product", path = "/api/product")
public interface ProductApi {

    @GetMapping("/{productId}")
    Result<ProductDTO> getById(@PathVariable("productId") Long productId);

    @GetMapping("/sku/{skuId}")
    Result<SkuDTO> getSkuById(@PathVariable("skuId") Long skuId);

    /**
     * 管理端商品分页（mall-admin 调用）。
     *
     * <p>用 POST + body 而不是 GET + 一堆 query 参数：条件字段多（含可空的 status/deleted/productId），
     * POST body 让可选字段的表达更清晰，也避免 Feign 对可空 query 参数的编码歧义。
     */
    @PostMapping("/admin/page")
    Result<PageResult<ProductDTO>> adminPage(@RequestBody AdminProductPageQuery query);

    /** 管理端 Tab 角标：各状态商品数量（一次拿全）。 */
    @GetMapping("/admin/status-counts")
    Result<AdminProductStatusCounts> adminStatusCounts();

    /** 分类列表（发布商品的必填下拉，见 G-08）。 */
    @GetMapping("/admin/category/list")
    Result<List<CategoryDTO>> listCategories();

    /** 品牌列表。 */
    @GetMapping("/admin/brand/list")
    Result<List<BrandDTO>> listBrands();

    /**
     * 按商品 ID 批量取 SKU（管理端列表聚合库存用）。
     *
     * <p>一次拿整页商品的 SKU 与 SKU 数量，避免逐商品查询造成 N+1（见计划文档 8.2）。
     */
    @PostMapping("/admin/sku-batch")
    Result<Map<Long, List<SkuDTO>>> adminSkuBatch(@RequestBody List<Long> productIds);

    /**
     * 按 SKU ID 批量取 SKU（管理端库存流水补 SKU 编码/名称用）。
     *
     * <p>流水在 mall_inventory、SKU 在 mall_product，跨库不能 JOIN，只能由 BFF 二次批量补齐
     * （与商品列表聚合库存同一套路数）。
     */
    @PostMapping("/admin/sku-by-ids")
    Result<List<SkuDTO>> skuByIds(@RequestBody List<Long> skuIds);

    /**
     * 新增商品（管理端，含 SKU 与库存初始化）。
     *
     * <p>挂在 {@code /admin} 下而非根路径：BFF 经 {@code lb://mall-product} 直连，
     * 不受"网关对外只放行 GET {@code /api/product/**}"的限制，用 {@code /admin/**} 命名保持隔离约定一致。
     */
    @PostMapping("/admin")
    Result<Long> create(@RequestBody ProductSaveRequest request);

    /** 编辑商品（管理端）：SKU 按差量更新（改/增/删），主键不变。 */
    @PutMapping("/{productId}")
    Result<Void> update(@PathVariable("productId") Long productId, @RequestBody ProductSaveRequest request);

    /* ---------------- 分类 / 品牌维护（管理端，M3） ---------------- */

    /** 新增分类；层级由下游按父分类推导。 */
    @PostMapping("/admin/category")
    Result<Long> createCategory(@RequestBody CategorySaveRequest request);

    @PutMapping("/admin/category/{categoryId}")
    Result<Void> updateCategory(@PathVariable("categoryId") Long categoryId,
                                @RequestBody CategorySaveRequest request);

    /** 删除分类（逻辑删除）：下游会挡"有子分类或有商品引用"。 */
    @DeleteMapping("/admin/category/{categoryId}")
    Result<Void> deleteCategory(@PathVariable("categoryId") Long categoryId);

    @PostMapping("/admin/brand")
    Result<Long> createBrand(@RequestBody BrandSaveRequest request);

    @PutMapping("/admin/brand/{brandId}")
    Result<Void> updateBrand(@PathVariable("brandId") Long brandId, @RequestBody BrandSaveRequest request);

    /** 删除品牌（逻辑删除）：下游会挡"有商品引用"。 */
    @DeleteMapping("/admin/brand/{brandId}")
    Result<Void> deleteBrand(@PathVariable("brandId") Long brandId);

    /**
     * 上下架（管理端）。
     *
     * <p>下游 {@code /api/product/{id}/status} 带 {@code @RequireStaff(OPERATOR)}，
     * 因此身份头必须经 BFF 的 Feign 拦截器透传，否则会被下游判为越权。
     */
    @PutMapping("/{productId}/status")
    Result<Void> updateStatus(@PathVariable("productId") Long productId,
                              @RequestParam("status") Integer status);

    /**
     * 逻辑删除（管理端）：只置 deleted，保留数据以便追溯。
     *
     * <p>同样是写接口，下游带 {@code @RequireStaff(OPERATOR)}，依赖身份头透传。
     */
    @DeleteMapping("/{productId}")
    Result<Void> softDelete(@PathVariable("productId") Long productId);
}
