package com.zhe.mall.cart.controller;

import com.zhe.mall.cart.dto.CartAddRequest;
import com.zhe.mall.cart.dto.CartCheckoutView;
import com.zhe.mall.cart.dto.CartPurchasedRemoveRequest;
import com.zhe.mall.cart.dto.CartSelectionRequest;
import com.zhe.mall.cart.dto.CartSelectionView;
import com.zhe.mall.cart.dto.CartUpdateRequest;
import com.zhe.mall.cart.dto.CartView;
import com.zhe.mall.cart.service.CartService;
import com.zhe.mall.common.auth.CurrentUserId;
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

/**
 * C 端购物车接口（UC-CART-01 / 02 / 03）。
 *
 * <p>所有接口都要求登录，且用户身份只从网关透传的 {@code @CurrentUserId} 获取；
 * 不接受任何客户端传入的 userId，避免越权读写他人购物车。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** 当前用户购物车（实时刷新价格 / 库存 / 上下架状态）。 */
    @GetMapping
    public Result<CartView> view(@CurrentUserId Long userId) {
        return Result.success(cartService.view(userId));
    }

    /** 加入购物车：同 SKU 累加。 */
    @PostMapping
    public Result<CartView> add(@CurrentUserId Long userId,
                                @Valid @RequestBody CartAddRequest request) {
        return Result.success(cartService.add(userId, request));
    }

    /** 批量修改数量 / 勾选；数量 <= 0 删除。 */
    @PutMapping
    public Result<CartView> update(@CurrentUserId Long userId,
                                   @Valid @RequestBody CartUpdateRequest request) {
        return Result.success(cartService.update(userId, request));
    }

    /** 批量勾选；skuIds 为空表示全选 / 全不选。 */
    @PutMapping("/selection")
    public Result<CartSelectionView> select(@CurrentUserId Long userId,
                                            @Valid @RequestBody CartSelectionRequest request) {
        return Result.success(cartService.select(userId, request));
    }

    /** 结算预览：勾选项合并为一次下单请求；全部未勾选拒绝。 */
    @PostMapping("/checkout")
    public Result<CartCheckoutView> checkout(@CurrentUserId Long userId) {
        return Result.success(cartService.checkout(userId));
    }

    /**
     * 下单成功后清理已购入的 SKU。
     *
     * <p>放在购物车服务而不是订单服务：购物车存储属于本服务，订单侧不应知道 Redis key 结构。
     * 清理失败不影响订单结果，前端只做提示。
     */
    @PostMapping("/purchased/remove")
    public Result<CartView> removePurchased(@CurrentUserId Long userId,
                                            @Valid @RequestBody CartPurchasedRemoveRequest request) {
        return Result.success(cartService.removePurchased(userId, request.skuIds()));
    }

    /** 删除单品。 */
    @DeleteMapping("/{skuId}")
    public Result<CartView> remove(@CurrentUserId Long userId,
                                   @PathVariable("skuId") Long skuId) {
        return Result.success(cartService.remove(userId, skuId));
    }
}
