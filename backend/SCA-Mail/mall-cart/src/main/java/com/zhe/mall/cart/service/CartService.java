package com.zhe.mall.cart.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.ProductApi;
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.cart.dto.CartAddRequest;
import com.zhe.mall.cart.dto.CartItemUpdate;
import com.zhe.mall.cart.dto.CartItemView;
import com.zhe.mall.cart.dto.CartSelectionRequest;
import com.zhe.mall.cart.dto.CartSelectionView;
import com.zhe.mall.cart.dto.CartUpdateRequest;
import com.zhe.mall.cart.dto.CartView;
import com.zhe.mall.cart.dto.CartCheckoutItem;
import com.zhe.mall.cart.dto.CartCheckoutView;
import com.zhe.mall.cart.store.CartProperties;
import com.zhe.mall.cart.store.CartStore;
import com.zhe.mall.cart.store.StoredCartItem;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车业务。
 *
 * <p>职责边界：
 * <ul>
 *   <li>Redis 只存"用户选择"（数量 / 勾选 / 加购时间），不存价格与库存；</li>
 *   <li>每次读取都从商品与库存服务刷新，避免价格、上下架、库存过期；</li>
 *   <li>失效行保留在购物车并标记 {@code invalid}，不自动删除，由用户决定；</li>
 *   <li>金额只统计"已勾选且有效"的行，与结算口径一致。</li>
 * </ul>
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private static final int STATUS_ON_SHELF = 1;

    private final CartStore cartStore;

    private final CartProperties properties;

    private final ProductApi productApi;

    private final InventoryApi inventoryApi;

    public CartService(CartStore cartStore, CartProperties properties,
                       ProductApi productApi, InventoryApi inventoryApi) {
        this.cartStore = cartStore;
        this.properties = properties;
        this.productApi = productApi;
        this.inventoryApi = inventoryApi;
    }

    /** 加购（UC-CART-01）：同 SKU 累加，上限 99；下架 / 售罄拒绝。 */
    public CartView add(Long userId, CartAddRequest request) {
        requireLogin(userId);
        SkuDTO sku = requirePurchasableSku(request.skuId());
        int available = requireAvailable(request.skuId());
        if (available <= 0) {
            throw new BusinessException(ResultCode.CART_SKU_UNAVAILABLE, "商品规格已售罄");
        }
        int result = cartStore.add(userId, sku.id(), request.quantity(),
                properties.getMaxQuantity(), available, properties.getMaxItems(), ttl());
        if (result == CartStore.RESULT_QUANTITY_EXCEEDED) {
            throw new BusinessException(ResultCode.CART_QUANTITY_EXCEEDED,
                    "购物车单品数量不能超过 " + properties.getMaxQuantity());
        }
        if (result == CartStore.RESULT_STOCK_NOT_ENOUGH) {
            throw new BusinessException(ResultCode.CART_STOCK_NOT_ENOUGH,
                    "库存不足，当前可售 " + available + " 件");
        }
        if (result == CartStore.RESULT_ITEM_LIMIT) {
            throw new BusinessException(ResultCode.CART_ITEM_LIMIT_EXCEEDED,
                    "购物车最多保存 " + properties.getMaxItems() + " 种商品");
        }
        if (result < 0) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND, "加入购物车失败，请重试");
        }
        return view(userId);
    }

    /**
     * 修改数量 / 勾选（UC-CART-02）：数量 <= 0 删除。
     *
     * <p>先完成全部校验与商品 / 库存查询，再写 Redis：校验阶段失败不会留下半更新状态。
     * 写入阶段仍可能因并发（同一 SKU 被另一请求改动）失败，此时返回明确错误而不是静默忽略。
     */
    public CartView update(Long userId, CartUpdateRequest request) {
        requireLogin(userId);
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "修改项不能为空");
        }
        // 先完成全部校验与商品 / 库存查询，再写 Redis：避免批量提交中途失败留下半更新状态
        List<Long> stockSkuIds = request.items().stream()
                .filter(item -> item != null && item.skuId() != null)
                .filter(item -> item.quantity() != null && item.quantity() > 0)
                .map(CartItemUpdate::skuId)
                .distinct()
                .toList();
        Map<Long, Integer> available = loadAvailable(stockSkuIds);
        List<ResolvedUpdate> resolved = new ArrayList<>();
        for (CartItemUpdate item : request.items()) {
            if (item == null || item.skuId() == null) {
                throw new BusinessException(ResultCode.PARAM_MISSING, "skuId 不能为空");
            }
            if (item.quantity() == null && item.selected() == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "数量和勾选状态至少提供一个");
            }
            StoredCartItem stored = cartStore.find(userId, item.skuId())
                    .orElseThrow(() -> new BusinessException(ResultCode.CART_ITEM_NOT_FOUND,
                            "购物车中不存在该商品: " + item.skuId()));
            int quantity = item.quantity() == null ? stored.quantity() : item.quantity();
            int stockLimit = Integer.MAX_VALUE;
            if (quantity > 0) {
                if (quantity > properties.getMaxQuantity()) {
                    throw new BusinessException(ResultCode.CART_QUANTITY_EXCEEDED,
                            "购物车单品数量不能超过 " + properties.getMaxQuantity());
                }
                stockLimit = availableOf(available, item.skuId());
                if (stockLimit <= 0) {
                    throw new BusinessException(ResultCode.CART_SKU_UNAVAILABLE, "商品规格已售罄");
                }
                if (quantity > stockLimit) {
                    throw new BusinessException(ResultCode.CART_STOCK_NOT_ENOUGH,
                            "库存不足，当前可售 " + stockLimit + " 件");
                }
            }
            resolved.add(new ResolvedUpdate(item.skuId(), quantity, item.selected(), stockLimit));
        }

        for (ResolvedUpdate update : resolved) {
            if (update.quantity() <= 0) {
                cartStore.remove(userId, update.skuId(), ttl());
                continue;
            }
            int result = cartStore.updateQuantity(userId, update.skuId(), update.quantity(),
                    properties.getMaxQuantity(), update.stockLimit(), ttl());
            if (result == CartStore.RESULT_QUANTITY_EXCEEDED) {
                throw new BusinessException(ResultCode.CART_QUANTITY_EXCEEDED,
                        "购物车单品数量不能超过 " + properties.getMaxQuantity());
            }
            if (result == CartStore.RESULT_STOCK_NOT_ENOUGH) {
                throw new BusinessException(ResultCode.CART_STOCK_NOT_ENOUGH, "库存不足");
            }
            if (result == CartStore.RESULT_NOT_FOUND) {
                throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND,
                        "购物车中不存在该商品: " + update.skuId());
            }
            if (update.selected() != null) {
                cartStore.setSelected(userId, List.of(update.skuId()), update.selected(), ttl());
            }
        }
        return view(userId);
    }

    /** 批量勾选；skuIds 为空表示全选 / 全不选。 */
    public CartSelectionView select(Long userId, CartSelectionRequest request) {
        requireLogin(userId);
        if (request == null || request.selected() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "selected 不能为空");
        }
        List<Long> skuIds = request.skuIds();
        if (skuIds == null || skuIds.isEmpty()) {
            skuIds = cartStore.items(userId, ttl()).stream().map(StoredCartItem::skuId).toList();
        }
        if (skuIds.isEmpty()) {
            throw new BusinessException(ResultCode.CART_SELECTION_EMPTY, "购物车为空");
        }
        int changed = cartStore.setSelected(userId, skuIds, request.selected(), ttl());
        return new CartSelectionView(changed, view(userId));
    }

    /** 删除单品。 */
    public CartView remove(Long userId, Long skuId) {
        requireLogin(userId);
        if (skuId == null || skuId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的 skuId: " + skuId);
        }
        cartStore.remove(userId, skuId, ttl());
        return view(userId);
    }

    /**
     * 购物车视图：实时刷新商品 / SKU / 库存，并汇总勾选金额。
     *
     * <p>库存服务不可用时 {@code available} 为 {@code null}（未知），不能当成 0：
     * 否则一次下游抖动就会把整辆购物车误报成"已售罄"。未知库存不判失效，
     * 最终以下单时订单服务的实时扣减为准。
     */
    public CartView view(Long userId) {
        requireLogin(userId);
        List<StoredCartItem> stored = cartStore.items(userId, ttl());
        if (stored.isEmpty()) {
            return new CartView(List.of(), 0, 0, amount(BigDecimal.ZERO), false);
        }
        List<Long> skuIds = stored.stream().map(StoredCartItem::skuId).toList();
        Map<Long, SkuDTO> skus = loadSkus(skuIds);
        List<Long> productIds = skus.values().stream().map(SkuDTO::productId).distinct().toList();
        Map<Long, ProductDTO> products = loadProducts(productIds);
        Map<Long, Integer> available = loadAvailable(skuIds);

        List<CartItemView> items = new ArrayList<>(stored.size());
        int totalQuantity = 0;
        int selectedCount = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;
        boolean allSelected = true;
        boolean hasValid = false;
        for (StoredCartItem row : stored) {
            SkuDTO sku = skus.get(row.skuId());
            ProductDTO product = sku == null ? null : products.get(sku.productId());
            Integer stock = available == null ? null : available.getOrDefault(row.skuId(), 0);
            String invalidReason = invalidReason(product, sku, stock);
            boolean invalid = !invalidReason.isEmpty();
            CartItemView item = new CartItemView(row.skuId(), sku == null ? null : sku.productId(),
                    product == null ? "商品已下架或不存在" : product.name(),
                    sku == null ? "规格已下架或不存在" : sku.name(),
                    sku == null ? null : sku.specJson(), imageOf(sku, product),
                    sku == null ? BigDecimal.ZERO : sku.price(),
                    sku == null ? BigDecimal.ZERO : sku.marketPrice(), row.quantity(), row.selected(),
                    stock, invalid, invalidReason);
            items.add(item);
            totalQuantity += row.quantity();
            if (row.selected() && !invalid) {
                selectedCount += row.quantity();
                selectedAmount = selectedAmount.add(item.price().multiply(BigDecimal.valueOf(row.quantity())));
            }
            if (!invalid) {
                hasValid = true;
                if (!row.selected()) {
                    allSelected = false;
                }
            }
        }
        return new CartView(items, totalQuantity, selectedCount, amount(selectedAmount),
                hasValid && allSelected);
    }

    /** 结算预览：返回可直接提交订单的勾选行，全部未勾选时拒绝。 */
    public CartCheckoutView checkout(Long userId) {
        CartView cart = view(userId);
        List<CartCheckoutItem> items = cart.items().stream()
                .filter(item -> Boolean.TRUE.equals(item.selected()) && !Boolean.TRUE.equals(item.invalid()))
                .map(item -> new CartCheckoutItem(item.skuId(), item.productName(), item.skuName(),
                        item.price(), item.quantity()))
                .toList();
        if (items.isEmpty()) {
            throw new BusinessException(ResultCode.CART_SELECTION_EMPTY, "请至少选择一件有效商品");
        }
        int totalQuantity = items.stream().mapToInt(CartCheckoutItem::quantity).sum();
        BigDecimal payAmount = items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartCheckoutView(items, totalQuantity, amount(payAmount));
    }

    /**
     * 下单成功后移除已购买 SKU（{@code POST /api/cart/purchased/remove}）。
     *
     * <p>清理的是"自己的购物车"，即使客户端谎报也不会影响他人；订单是否真的创建成功
     * 仍以订单服务为准，本接口不承担下单校验职责。
     */
    public CartView removePurchased(Long userId, List<Long> skuIds) {
        requireLogin(userId);
        if (skuIds != null) {
            skuIds.stream().filter(java.util.Objects::nonNull).distinct()
                    .forEach(skuId -> cartStore.remove(userId, skuId, ttl()));
        }
        return view(userId);
    }

    private SkuDTO requirePurchasableSku(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的 skuId: " + skuId);
        }
        Result<SkuDTO> result = productApi.getSkuById(skuId);
        if (result == null || !result.isSuccess() || result.data() == null) {
            throw new BusinessException(ResultCode.CART_SKU_UNAVAILABLE, "商品规格不存在或已下架");
        }
        SkuDTO sku = result.data();
        if (!Integer.valueOf(STATUS_ON_SHELF).equals(sku.status())) {
            throw new BusinessException(ResultCode.CART_SKU_UNAVAILABLE, "商品规格已下架");
        }
        Result<ProductDTO> productResult = productApi.getById(sku.productId());
        if (productResult == null || !productResult.isSuccess() || productResult.data() == null) {
            throw new BusinessException(ResultCode.CART_PRODUCT_UNAVAILABLE, "商品不存在或已下架");
        }
        if (!Integer.valueOf(STATUS_ON_SHELF).equals(productResult.data().status())) {
            throw new BusinessException(ResultCode.CART_PRODUCT_UNAVAILABLE, "商品已下架");
        }
        return sku;
    }

    /** 单 SKU 可售量；null（库存服务不可用）按下游故障处理，不能猜成 0。 */
    private int requireAvailable(Long skuId) {
        Map<Long, Integer> available = loadAvailable(List.of(skuId));
        return availableOf(available, skuId);
    }

    private int availableOf(Map<Long, Integer> available, Long skuId) {
        if (available == null) {
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                    "库存服务暂不可用，请稍后重试");
        }
        return available.getOrDefault(skuId, 0);
    }

    private Map<Long, SkuDTO> loadSkus(List<Long> skuIds) {
        try {
            Result<List<SkuDTO>> result = productApi.getSkusByIds(skuIds);
            if (result == null || !result.isSuccess() || result.data() == null) {
                log.warn("购物车批量查询 SKU 失败: skuIds={}, message={}", skuIds,
                        result == null ? "无响应" : result.message());
                return Map.of();
            }
            return result.data().stream().collect(Collectors.toMap(SkuDTO::id, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("购物车批量查询 SKU 异常: skuIds={}, error={}", skuIds, e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, ProductDTO> loadProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        try {
            Result<List<ProductDTO>> result = productApi.getByIds(productIds);
            if (result == null || !result.isSuccess() || result.data() == null) {
                log.warn("购物车批量查询商品失败: productIds={}, message={}", productIds,
                        result == null ? "无响应" : result.message());
                return Map.of();
            }
            return result.data().stream()
                    .collect(Collectors.toMap(ProductDTO::id, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("购物车批量查询商品异常: productIds={}, error={}", productIds, e.getMessage());
            return Map.of();
        }
    }

    /** 返回 null 表示库存服务不可用（未知），空 Map 表示确实都没有库存记录。 */
    private Map<Long, Integer> loadAvailable(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Result<Map<Long, Integer>> result = inventoryApi.getAvailableBatch(new InventoryBatchRequest(skuIds));
            if (result == null || !result.isSuccess() || result.data() == null) {
                log.warn("购物车批量查询库存失败: skuIds={}, message={}", skuIds,
                        result == null ? "无响应" : result.message());
                return null;
            }
            return result.data();
        } catch (RuntimeException e) {
            log.warn("购物车批量查询库存异常: skuIds={}, error={}", skuIds, e.getMessage());
            return null;
        }
    }

    private String invalidReason(ProductDTO product, SkuDTO sku, Integer stock) {
        if (product == null) {
            return "商品已下架或不存在";
        }
        if (!Integer.valueOf(STATUS_ON_SHELF).equals(product.status())) {
            return "商品已下架";
        }
        if (sku == null) {
            return "规格已下架或不存在";
        }
        if (!Integer.valueOf(STATUS_ON_SHELF).equals(sku.status())) {
            return "规格已下架";
        }
        if (stock != null && stock <= 0) {
            return "商品已售罄";
        }
        return "";
    }

    private String imageOf(SkuDTO sku, ProductDTO product) {
        if (sku != null && sku.image() != null && !sku.image().isBlank()) {
            return sku.image();
        }
        return product == null ? null : product.mainImage();
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Duration ttl() {
        return Duration.ofSeconds(properties.getTtlSeconds());
    }

    private void requireLogin(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    private record ResolvedUpdate(Long skuId, int quantity, Boolean selected, int stockLimit) {
    }
}
