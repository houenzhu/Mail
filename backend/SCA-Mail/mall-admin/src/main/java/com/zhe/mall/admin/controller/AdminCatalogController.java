package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.service.AdminProductService;
import com.zhe.mall.api.dto.BrandDTO;
import com.zhe.mall.api.dto.BrandSaveRequest;
import com.zhe.mall.api.dto.CategoryDTO;
import com.zhe.mall.api.dto.CategorySaveRequest;
import com.zhe.mall.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台目录数据接口（分类 / 品牌），对外统一 {@code /api/admin/**}。
 *
 * <p>挂在 {@code /api/admin} 而非 {@code /api/admin/product} 下，是因为这两份是**共享的主数据**：
 * 商品发布、筛选、列表都取它，未来其他后台模块（如活动）也会用；
 * 放在商品前缀下会让人误以为只属于商品域。
 *
 * <p>鉴权与 {@link AdminProductController} 一致：网关保证已登录；BFF 不做角色判断，
 * 写接口的角色校验由下游 mall-product 的 {@code @RequireStaff(OPERATOR)} 承担（规则只有一份），
 * 身份头继续透传给 mall-product。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminProductService adminProductService;

    public AdminCatalogController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    /** 分类列表（发布商品的必填下拉）。 */
    @GetMapping("/category/list")
    public Result<List<CategoryDTO>> categories() {
        return Result.success(adminProductService.listCategories());
    }

    /** 品牌列表。 */
    @GetMapping("/brand/list")
    public Result<List<BrandDTO>> brands() {
        return Result.success(adminProductService.listBrands());
    }

    /* ---------------- 分类维护（写接口透传下游，角色校验在下游） ---------------- */

    @PostMapping("/category")
    public Result<Long> createCategory(@Valid @RequestBody CategorySaveRequest request) {
        return Result.success(adminProductService.createCategory(request));
    }

    @PutMapping("/category/{categoryId}")
    public Result<Void> updateCategory(@PathVariable("categoryId") Long categoryId,
                                       @Valid @RequestBody CategorySaveRequest request) {
        adminProductService.updateCategory(categoryId, request);
        return Result.success();
    }

    /** 删除分类（逻辑删除）：有子分类或有商品引用时下游会拒绝，并给出可读原因。 */
    @DeleteMapping("/category/{categoryId}")
    public Result<Void> deleteCategory(@PathVariable("categoryId") Long categoryId) {
        adminProductService.deleteCategory(categoryId);
        return Result.success();
    }

    /* ---------------- 品牌维护 ---------------- */

    @PostMapping("/brand")
    public Result<Long> createBrand(@Valid @RequestBody BrandSaveRequest request) {
        return Result.success(adminProductService.createBrand(request));
    }

    @PutMapping("/brand/{brandId}")
    public Result<Void> updateBrand(@PathVariable("brandId") Long brandId,
                                    @Valid @RequestBody BrandSaveRequest request) {
        adminProductService.updateBrand(brandId, request);
        return Result.success();
    }

    @DeleteMapping("/brand/{brandId}")
    public Result<Void> deleteBrand(@PathVariable("brandId") Long brandId) {
        adminProductService.deleteBrand(brandId);
        return Result.success();
    }
}