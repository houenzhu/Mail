package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.dto.AdminProductDetail;
import com.zhe.mall.admin.dto.AdminProductPage;
import com.zhe.mall.admin.service.AdminProductService;
import com.zhe.mall.api.dto.AdminProductPageQuery;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.common.result.Result;
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

/**
 * 后台商品管理接口（对外统一 {@code /api/admin/**}，见需求文档 655 行约定与计划文档第十三章）。
 *
 * <p>当前鉴权情况：
 * <ol>
 *   <li><b>网关</b>：{@code /api/admin/**} 不在白名单里，未登录一律 401（Sa-Token 会话校验）；</li>
 *   <li><b>服务端写接口</b>：本 BFF 透传身份头，由下游写方法上的 {@code @RequireStaff} 做角色校验；</li>
 *   <li><b>读取接口</b>：当前 BFF 和部分下游读取方法尚未统一要求 staff 角色。登录校验不能代替角色授权；
 *       这是已知权限缺口，修复前不要将商品管理读取接口视为生产安全。见需求文档 30.2。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/product")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    /**
     * 管理端商品分页。
     *
     * <p>用 POST + body：筛选条件多且大多可选，body 比一长串 query 参数更清晰，
     * 也避免"空字符串 vs 未传"在前端产生歧义。
     */
    @PostMapping("/page")
    public Result<AdminProductPage> page(@RequestBody(required = false) AdminProductPageQuery query) {
        return Result.success(adminProductService.page(
                query == null ? new AdminProductPageQuery(null, null, null, null, null, 0, 1, 10) : query));
    }

    /**
     * 状态角标计数。与分页放在一起是为了前端一次请求拿全；
     * 单独暴露一个 GET 也便于将来只刷新角标。
     */
    @GetMapping("/status-counts")
    public Result<AdminProductPage.Counts> statusCounts() {
        return Result.success(adminProductService.counts());
    }

    /** 单商品 SKU 列表（编辑回显）。 */
    @GetMapping("/sku")
    public Result<List<SkuDTO>> skus(@RequestParam("productId") Long productId) {
        return Result.success(adminProductService.skus(productId));
    }

    /**
     * 商品详情（编辑回显）：商品主体 + 真实 SKU 列表（此前编辑页是假回显，见 M3 计划）。
     *
     * <p>路径模板 {@code /{productId}} 与上面的 {@code /status-counts}、{@code /sku} 并存没问题：
     * Spring 的路径匹配里**字面量段优先于变量段**，因此固定路径不会被当成 productId。
     */
    @GetMapping("/{productId}")
    public Result<AdminProductDetail> detail(@PathVariable("productId") Long productId) {
        return Result.success(adminProductService.detail(productId));
    }

    /**
     * 新增商品（含 SKU 与库存初始化）。
     *
     * <p>角色校验仍在下游 {@code @RequireStaff(OPERATOR)}：BFF 只透传身份头，不重复判断。
     * 用到 {@code @Valid} 是给前端一次性、可读的参数错误（下游还会再校验一次）。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductSaveRequest request) {
        return Result.success(adminProductService.create(request));
    }

    /** 编辑商品（SKU 差量更新：改 / 增 / 删，主键不变）。 */
    @PutMapping("/{productId}")
    public Result<Void> update(@PathVariable("productId") Long productId,
                               @Valid @RequestBody ProductSaveRequest request) {
        adminProductService.update(productId, request);
        return Result.success();
    }

    /**
     * 上下架。
     *
     * <p>本 BFF 不做角色判断，只把网关注入的身份头透传给下游，
     * 由 mall-product 的 {@code @RequireStaff(OPERATOR)} 做唯一一处角色校验（规则只有一份）。
     */
    @PutMapping("/{productId}/status")
    public Result<Void> updateStatus(@PathVariable("productId") Long productId,
                                     @RequestParam("status") Integer status) {
        adminProductService.updateStatus(productId, status);
        return Result.success();
    }

    /** 逻辑删除。角色校验同样在下游。 */
    @DeleteMapping("/{productId}")
    public Result<Void> delete(@PathVariable("productId") Long productId) {
        adminProductService.delete(productId);
        return Result.success();
    }
}
