package com.zhe.mall.cart.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.ProductApi;
import com.zhe.mall.api.dto.InventoryBatchRequest;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.cart.dto.CartAddRequest;
import com.zhe.mall.cart.dto.CartCheckoutView;
import com.zhe.mall.cart.dto.CartItemUpdate;
import com.zhe.mall.cart.dto.CartSelectionRequest;
import com.zhe.mall.cart.dto.CartUpdateRequest;
import com.zhe.mall.cart.dto.CartView;
import com.zhe.mall.cart.store.CartProperties;
import com.zhe.mall.cart.store.CartStore;
import com.zhe.mall.cart.store.StoredCartItem;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 购物车核心业务规则（UC-CART-01 / 02 / 03）。 */
class CartServiceTest {

    private static final Long USER_ID = 9L;

    private static final Long SKU_ID = 1001L;

    private static final Long PRODUCT_ID = 1L;

    private ProductApi productApi;

    private InventoryApi inventoryApi;

    private InMemoryCartStore cartStore;

    private CartProperties properties;

    private CartService service;

    @BeforeEach
    void setUp() {
        productApi = mock(ProductApi.class);
        inventoryApi = mock(InventoryApi.class);
        cartStore = new InMemoryCartStore();
        properties = new CartProperties();
        service = new CartService(cartStore, properties, productApi, inventoryApi);

        when(productApi.getSkuById(SKU_ID)).thenReturn(Result.success(sku(SKU_ID, 1, "499.00")));
        when(productApi.getById(PRODUCT_ID)).thenReturn(Result.success(product(1)));
        when(productApi.getSkusByIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return Result.success(ids.stream().map(id -> sku(id, 1, "499.00")).toList());
        });
        when(productApi.getByIds(anyList())).thenReturn(Result.success(List.of(product(1))));
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.success(Map.of(SKU_ID, 10)));
    }

    @Test
    @DisplayName("同 SKU 连续加购累加数量")
    void accumulatesSameSku() {
        service.add(USER_ID, new CartAddRequest(SKU_ID, 1));
        CartView cart = service.add(USER_ID, new CartAddRequest(SKU_ID, 2));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(3);
        assertThat(cart.totalQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("累加后超过 99 返回业务错误，且不写入")
    void rejectsQuantityOverLimit() {
        cartStore.put(USER_ID, SKU_ID, 98, true);

        assertThatThrownBy(() -> service.add(USER_ID, new CartAddRequest(SKU_ID, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CART_QUANTITY_EXCEEDED.getCode());
        assertThat(cartStore.find(USER_ID, SKU_ID).orElseThrow().quantity()).isEqualTo(98);
    }

    @Test
    @DisplayName("商品已下架拒绝加购")
    void rejectsOffShelfProduct() {
        when(productApi.getById(PRODUCT_ID)).thenReturn(Result.success(product(0)));

        assertThatThrownBy(() -> service.add(USER_ID, new CartAddRequest(SKU_ID, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CART_PRODUCT_UNAVAILABLE.getCode());
        assertThat(cartStore.items(USER_ID, Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("SKU 不存在拒绝加购")
    void rejectsMissingSku() {
        when(productApi.getSkuById(SKU_ID)).thenReturn(Result.failure(ResultCode.SKU_NOT_FOUND));

        assertThatThrownBy(() -> service.add(USER_ID, new CartAddRequest(SKU_ID, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CART_SKU_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("库存为 0 拒绝加购")
    void rejectsSoldOutSku() {
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.success(Map.of(SKU_ID, 0)));

        assertThatThrownBy(() -> service.add(USER_ID, new CartAddRequest(SKU_ID, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CART_SKU_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("数量改为 0 删除该 SKU")
    void deletesWhenQuantityIsZero() {
        cartStore.put(USER_ID, SKU_ID, 2, true);

        CartView cart = service.update(USER_ID, new CartUpdateRequest(
                List.of(new CartItemUpdate(SKU_ID, 0, null))));

        assertThat(cart.items()).isEmpty();
        assertThat(cartStore.find(USER_ID, SKU_ID)).isEmpty();
    }

    @Test
    @DisplayName("批量勾选后金额只统计勾选且有效的商品")
    void calculatesSelectedAmount() {
        cartStore.put(USER_ID, SKU_ID, 2, false);

        var result = service.select(USER_ID, new CartSelectionRequest(List.of(SKU_ID), true));

        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.cart().selectedCount()).isEqualTo(2);
        assertThat(result.cart().selectedAmount()).isEqualByComparingTo("998.00");
        assertThat(result.cart().allSelected()).isTrue();
    }

    @Test
    @DisplayName("失效商品保留在列表但不计入勾选金额，结算时全部失效则拒绝")
    void keepsInvalidItemsButRejectsCheckout() {
        cartStore.put(USER_ID, SKU_ID, 1, true);
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.success(Map.of(SKU_ID, 0)));

        CartView cart = service.view(USER_ID);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).invalid()).isTrue();
        assertThat(cart.items().get(0).invalidReason()).contains("售罄");
        assertThat(cart.selectedAmount()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.checkout(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CART_SELECTION_EMPTY.getCode());
    }

    @Test
    @DisplayName("结算预览合并勾选项为一次下单请求")
    void buildsCheckoutItems() {
        cartStore.put(USER_ID, SKU_ID, 2, true);

        CartCheckoutView checkout = service.checkout(USER_ID);

        assertThat(checkout.items()).hasSize(1);
        assertThat(checkout.items().get(0).skuId()).isEqualTo(SKU_ID);
        assertThat(checkout.items().get(0).quantity()).isEqualTo(2);
        assertThat(checkout.payAmount()).isEqualByComparingTo("998.00");
    }

    @Test
    @DisplayName("未登录（userId 为空）直接拒绝")
    void rejectsAnonymous() {
        assertThatThrownBy(() -> service.view(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("库存服务不可用：加购报下游不可用，而不是谎报售罄")
    void addFailsClosedWhenInventoryDown() {
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "库存服务不可用"));

        assertThatThrownBy(() -> service.add(USER_ID, new CartAddRequest(SKU_ID, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DOWNSTREAM_UNAVAILABLE.getCode());
        assertThat(cartStore.items(USER_ID, Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("库存服务不可用：购物车仍可展示，库存为未知且不判失效")
    void viewKeepsItemsWhenInventoryDown() {
        cartStore.put(USER_ID, SKU_ID, 2, true);
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenReturn(Result.failure(ResultCode.DOWNSTREAM_UNAVAILABLE, "库存服务不可用"));

        CartView cart = service.view(USER_ID);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).available()).isNull();
        assertThat(cart.items().get(0).invalid()).isFalse();
        assertThat(cart.selectedAmount()).isEqualByComparingTo("998.00");
    }

    @Test
    @DisplayName("失效行不影响全选：只剩有效行都已勾选时 allSelected 为 true")
    void ignoredInvalidRowsWhenCalculatingAllSelected() {
        cartStore.put(USER_ID, SKU_ID, 1, true);
        cartStore.put(USER_ID, 999L, 1, false);
        when(productApi.getSkusByIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return Result.success(ids.stream()
                    .filter(id -> id.equals(SKU_ID))
                    .map(id -> sku(id, 1, "499.00"))
                    .toList());
        });
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class)))
                .thenAnswer(invocation -> {
                    InventoryBatchRequest request = invocation.getArgument(0);
                    Map<Long, Integer> stock = new LinkedHashMap<>();
                    request.skuIds().forEach(id -> stock.put(id, id.equals(SKU_ID) ? 10 : 0));
                    return Result.success(stock);
                });

        CartView cart = service.view(USER_ID);

        assertThat(cart.items()).hasSize(2);
        assertThat(cart.items().get(0).invalid()).isFalse();
        assertThat(cart.items().get(1).invalid()).isTrue();
        assertThat(cart.allSelected()).isTrue();
        assertThat(cart.selectedAmount()).isEqualByComparingTo("499.00");
    }

    @Test
    @DisplayName("批量改数量只查一次库存，避免逐条调用造成 N+1")
    void batchUpdateLoadsStockOnce() {
        cartStore.put(USER_ID, SKU_ID, 1, true);
        cartStore.put(USER_ID, 1002L, 1, true);
        when(productApi.getSkusByIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return Result.success(ids.stream().map(id -> sku(id, 1, "499.00")).toList());
        });
        when(inventoryApi.getAvailableBatch(any(InventoryBatchRequest.class))).thenAnswer(invocation -> {
            InventoryBatchRequest request = invocation.getArgument(0);
            Map<Long, Integer> stock = new LinkedHashMap<>();
            request.skuIds().forEach(id -> stock.put(id, 10));
            return Result.success(stock);
        });

        CartView cart = service.update(USER_ID, new CartUpdateRequest(List.of(
                new CartItemUpdate(SKU_ID, 3, null),
                new CartItemUpdate(1002L, 4, null))));

        assertThat(cart.items()).extracting(item -> item.quantity()).containsExactly(3, 4);
        // update 内部写完后会刷新一次视图；再加一次校验查询，共 2 次，而不是"每条一行一次"
        verify(inventoryApi, times(2)).getAvailableBatch(any(InventoryBatchRequest.class));
    }

    @Test
    @DisplayName("下单后清理已购 SKU，只删除指定的行")
    void removePurchasedOnlyGivenSkus() {
        cartStore.put(USER_ID, SKU_ID, 1, true);
        cartStore.put(USER_ID, 1002L, 2, true);

        CartView cart = service.removePurchased(USER_ID, List.of(SKU_ID));

        assertThat(cart.items()).extracting(item -> item.skuId()).containsExactly(1002L);
        assertThat(cartStore.find(USER_ID, SKU_ID)).isEmpty();
    }

    private static SkuDTO sku(Long id, int status, String price) {
        return new SkuDTO(id, PRODUCT_ID, "SKU-" + id, "黑色 / 标准版", "{\"颜色\":\"黑色\"}",
                new BigDecimal(price), new BigDecimal("599.00"), "https://img.example.com/sku.png", status);
    }

    private static ProductDTO product(int status) {
        return new ProductDTO(PRODUCT_ID, "无线降噪耳机 Pro", "主动降噪", "https://img.example.com/p.png",
                new BigDecimal("499.00"), new BigDecimal("499.00"), status, 1L, 1L, 100,
                "2026-11-01 10:00:00", "2026-11-01 10:00:00");
    }

    /** 内存实现：规则与 Redis Lua 对齐，便于验证业务分支。 */
    private static class InMemoryCartStore implements CartStore {

        private final Map<Long, Map<Long, StoredCartItem>> data = new LinkedHashMap<>();

        void put(Long userId, Long skuId, int quantity, boolean selected) {
            data.computeIfAbsent(userId, key -> new LinkedHashMap<>())
                    .put(skuId, new StoredCartItem(skuId, quantity, selected));
        }

        @Override
        public int add(Long userId, Long skuId, int quantity, int maxQuantity, int stockLimit,
                       int maxItems, Duration ttl) {
            Map<Long, StoredCartItem> cart = data.computeIfAbsent(userId, key -> new LinkedHashMap<>());
            StoredCartItem current = cart.get(skuId);
            int next = (current == null ? 0 : current.quantity()) + quantity;
            if (next > maxQuantity) return RESULT_QUANTITY_EXCEEDED;
            if (next > stockLimit) return RESULT_STOCK_NOT_ENOUGH;
            if (current == null && cart.size() >= maxItems) return RESULT_ITEM_LIMIT;
            cart.put(skuId, new StoredCartItem(skuId, next, current == null || current.selected()));
            return next;
        }

        @Override
        public int updateQuantity(Long userId, Long skuId, int quantity, int maxQuantity,
                                  int stockLimit, Duration ttl) {
            Map<Long, StoredCartItem> cart = data.get(userId);
            StoredCartItem current = cart == null ? null : cart.get(skuId);
            if (quantity <= 0) {
                if (cart == null || cart.remove(skuId) == null) return RESULT_NOT_FOUND;
                return 0;
            }
            if (current == null) return RESULT_NOT_FOUND;
            if (quantity > maxQuantity) return RESULT_QUANTITY_EXCEEDED;
            if (quantity > stockLimit) return RESULT_STOCK_NOT_ENOUGH;
            cart.put(skuId, new StoredCartItem(skuId, quantity, current.selected()));
            return quantity;
        }

        @Override
        public boolean remove(Long userId, Long skuId, Duration ttl) {
            Map<Long, StoredCartItem> cart = data.get(userId);
            return cart != null && cart.remove(skuId) != null;
        }

        @Override
        public List<StoredCartItem> items(Long userId, Duration ttl) {
            return new ArrayList<>(data.getOrDefault(userId, Map.of()).values());
        }

        @Override
        public Optional<StoredCartItem> find(Long userId, Long skuId) {
            return Optional.ofNullable(data.getOrDefault(userId, Map.of()).get(skuId));
        }

        @Override
        public int setSelected(Long userId, List<Long> skuIds, boolean selected, Duration ttl) {
            Map<Long, StoredCartItem> cart = data.get(userId);
            if (cart == null) return 0;
            int changed = 0;
            for (Long skuId : skuIds) {
                StoredCartItem current = cart.get(skuId);
                if (current != null && current.selected() != selected) {
                    cart.put(skuId, new StoredCartItem(skuId, current.quantity(), selected));
                    changed++;
                }
            }
            return changed;
        }
    }
}
