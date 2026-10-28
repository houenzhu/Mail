package com.zhe.mall.product.controller;

import com.zhe.mall.api.dto.AdminProductPageQuery;
import com.zhe.mall.api.dto.AdminProductStatusCounts;
import com.zhe.mall.api.dto.BrandDTO;
import com.zhe.mall.api.dto.CategoryDTO;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.product.dto.ProductDetailVO;
import com.zhe.mall.product.dto.ProductPageQuery;
import com.zhe.mall.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{productId}")
    public Result<ProductDTO> detail(@PathVariable("productId") Long productId) {
        return Result.success(productService.detail(productId));
    }

    @GetMapping("/{productId}/detail")
    public Result<ProductDetailVO> detailWithSkus(@PathVariable("productId") Long productId) {
        return Result.success(productService.detailWithSkus(productId));
    }

    @GetMapping("/page")
    public Result<PageResult<ProductDTO>> page(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        ProductPageQuery query = new ProductPageQuery();
        query.setCategoryId(categoryId);
        query.setBrandId(brandId);
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.success(productService.page(query));
    }

    @GetMapping("/sku/{skuId}")
    public Result<SkuDTO> sku(@PathVariable("skuId") Long skuId) {
        return Result.success(productService.sku(skuId));
    }

    /* ---------------- 管理端数据接口（由 mall-admin 经 Feign 调用） ----------------
     *
     * 管理读取接口必须在服务端校验 staff 角色；网关公开商品白名单也只覆盖明确的 C 端路由，
     * 不能把路径命名或 Feign 调用约定当成安全边界。
     */

    /** 管理端商品分页（含已删除/按 ID 精确查）。 */
    @PostMapping("/admin/page")
    @RequireStaff
    public Result<PageResult<ProductDTO>> adminPage(@RequestBody AdminProductPageQuery query) {
        return Result.success(productService.adminPage(query));
    }

    /** 管理端 Tab 角标：各状态商品数量（一次拿全，避免前端发多个 count 请求）。 */
    @GetMapping("/admin/status-counts")
    @RequireStaff
    public Result<AdminProductStatusCounts> adminStatusCounts() {
        return Result.success(productService.statusCounts());
    }

    /** 分类列表（发布商品必填下拉；此前只有表没有接口，见 G-08）。 */
    @GetMapping("/category/list")
    public Result<List<CategoryDTO>> categories() {
        return Result.success(productService.listCategories());
    }

    /** 品牌列表。 */
    @GetMapping("/brand/list")
    public Result<List<BrandDTO>> brands() {
        return Result.success(productService.listBrands());
    }

    /** 按商品 ID 批量取 SKU（管理端列表聚合库存，一次拿整页，避免 N+1）。 */
    @PostMapping("/admin/sku-batch")
    @RequireStaff
    public Result<Map<Long, List<SkuDTO>>> adminSkuBatch(@RequestBody List<Long> productIds) {
        return Result.success(productService.skusByProductIds(productIds));
    }

    /** 按 SKU ID 批量取 SKU（管理端库存流水补 SKU 编码/名称用，跨库不能 JOIN）。 */
    @PostMapping("/admin/sku-by-ids")
    @RequireStaff
    public Result<List<SkuDTO>> adminSkuByIds(@RequestBody List<Long> skuIds) {
        return Result.success(productService.skusByIds(skuIds));
    }

    /**
     * 新增商品（含 SKU 与库存初始化）。
     *
     * <p>写接口必须限后台人员：此前只要求登录，任何买家都能新增商品（越权缺陷，见需求文档 21.3 / 计划文档 G-01）。
     */
    @PostMapping("/admin")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Long> create(@Valid @RequestBody ProductSaveRequest request) {
        return Result.success(productService.create(request));
    }

    /**
     * 编辑商品（管理端）：SKU 差量更新（改/增/删），主键不变。
     *
     * <p>与新增一样限运营与管理员；身份由网关下发、BFF 透传（见计划文档 G-03）。
     */
    @PutMapping("/{productId}")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> update(@PathVariable("productId") Long productId,
                               @Valid @RequestBody ProductSaveRequest request) {
        productService.update(productId, request);
        return Result.success();
    }

    /** 上下架：仅运营与管理员。 */
    @PutMapping("/{productId}/status")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> updateStatus(@PathVariable("productId") Long productId,
                                     @RequestParam("status") Integer status) {
        productService.updateStatus(productId, status);
        return Result.success();
    }

    /** 逻辑删除：仅运营与管理员。 */
    @DeleteMapping("/{productId}")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> delete(@PathVariable("productId") Long productId) {
        productService.delete(productId);
        return Result.success();
    }
}
