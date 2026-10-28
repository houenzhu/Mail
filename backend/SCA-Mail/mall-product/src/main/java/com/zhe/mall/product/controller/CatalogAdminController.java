package com.zhe.mall.product.controller;

import com.zhe.mall.api.dto.BrandSaveRequest;
import com.zhe.mall.api.dto.CategorySaveRequest;
import com.zhe.mall.api.dto.BrandDTO;
import com.zhe.mall.api.dto.CategoryDTO;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.product.service.BrandService;
import com.zhe.mall.product.service.CategoryService;
import com.zhe.mall.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分类 / 品牌维护接口（管理端），只由 mall-admin 经 Feign 直连调用。
 *
 * <p>挂在 {@code /api/product/admin/**} 下：与 {@link ProductController} 的管理端接口同一约定。
 * 读取和写入都在服务端做角色校验；网关只负责外部路由和会话鉴权。
 *
 * <p>写接口全部限运营与管理员（{@code @RequireStaff(OPERATOR)}）：身份头由网关下发、
 * 由 mall-admin 的 Feign 拦截器透传。
 */
@RestController
@RequestMapping("/api/product/admin")
public class CatalogAdminController {

    private final CategoryService categoryService;

    private final BrandService brandService;

    private final ProductService productService;

    public CatalogAdminController(CategoryService categoryService, BrandService brandService,
                                  ProductService productService) {
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.productService = productService;
    }

    /** 管理端分类主数据读取；与公开 C 端字典接口分开，避免 GET 白名单绕过后台授权。 */
    @GetMapping("/category/list")
    @RequireStaff
    public Result<java.util.List<CategoryDTO>> listCategories() {
        return Result.success(productService.listCategories());
    }

    /** 管理端品牌主数据读取；与公开 C 端字典接口分开，避免 GET 白名单绕过后台授权。 */
    @GetMapping("/brand/list")
    @RequireStaff
    public Result<java.util.List<BrandDTO>> listBrands() {
        return Result.success(productService.listBrands());
    }

    @PostMapping("/category")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Long> createCategory(@Valid @RequestBody CategorySaveRequest request) {
        return Result.success(categoryService.create(request));
    }

    @PutMapping("/category/{categoryId}")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> updateCategory(@PathVariable("categoryId") Long categoryId,
                                       @Valid @RequestBody CategorySaveRequest request) {
        categoryService.update(categoryId, request);
        return Result.success();
    }

    @DeleteMapping("/category/{categoryId}")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> deleteCategory(@PathVariable("categoryId") Long categoryId) {
        categoryService.delete(categoryId);
        return Result.success();
    }

    @PostMapping("/brand")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Long> createBrand(@Valid @RequestBody BrandSaveRequest request) {
        return Result.success(brandService.create(request));
    }

    @PutMapping("/brand/{brandId}")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> updateBrand(@PathVariable("brandId") Long brandId,
                                    @Valid @RequestBody BrandSaveRequest request) {
        brandService.update(brandId, request);
        return Result.success();
    }

    @DeleteMapping("/brand/{brandId}")
    @RequireStaff(UserRole.OPERATOR)
    public Result<Void> deleteBrand(@PathVariable("brandId") Long brandId) {
        brandService.delete(brandId);
        return Result.success();
    }
}
