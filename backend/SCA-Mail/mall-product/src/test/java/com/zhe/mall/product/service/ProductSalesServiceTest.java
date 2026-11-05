package com.zhe.mall.product.service;

import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.cache.MallCache;
import com.zhe.mall.product.mapper.ProductSalesMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 累计销量回写（G-12）的幂等与聚合规则。
 *
 * <p>这是"钱"相关的数据：重复投递把销量加两遍，运营看到的就是假数据。
 * 因此重点断言三件事：同一订单重复事件不再累加、同一商品多 SKU 合并成一笔、
 * 商品不存在时不能让认领记录静默留下（必须回滚并重投）。
 */
class ProductSalesServiceTest {

    @Test
    @DisplayName("同一订单的同一商品只累加一次")
    void appliesOncePerOrderAndProduct() {
        StubSalesMapper mapper = new StubSalesMapper();
        ProductSalesService service = new ProductSalesService(mapper, new StubCache());
        MessageEvents.OrderPaidEvent event = event("ORDER-1",
                new MessageEvents.SalesItem(1L, 1001L, 2),
                new MessageEvents.SalesItem(1L, 1002L, 1));

        assertThat(service.applyOrderPaid(event)).isTrue();
        assertThat(service.applyOrderPaid(event)).isTrue();

        // SKU 1001(2) + 1002(1) 合并为商品 1 的 3 件，重复事件不再加
        assertThat(mapper.claims).containsExactly("ORDER-1:1");
        assertThat(mapper.totalSales(1L)).isEqualTo(3);
    }

    @Test
    @DisplayName("同商品多 SKU 合并为一次累加")
    void mergesSkusOfSameProduct() {
        StubSalesMapper mapper = new StubSalesMapper();
        ProductSalesService service = new ProductSalesService(mapper, new StubCache());

        service.applyOrderPaid(event("ORDER-2",
                new MessageEvents.SalesItem(9L, 9001L, 4),
                new MessageEvents.SalesItem(9L, 9002L, 6)));

        assertThat(mapper.updateCalls).isEqualTo(1);
        assertThat(mapper.totalSales(9L)).isEqualTo(10);
    }

    @Test
    @DisplayName("商品不存在时抛错回滚认领，交给 MQ 重投而不是静默丢销量")
    void failsWhenProductMissing() {
        StubSalesMapper mapper = new StubSalesMapper();
        mapper.missingProducts.add(5L);
        ProductSalesService service = new ProductSalesService(mapper, new StubCache());

        assertThatThrownBy(() -> service.applyOrderPaid(event("ORDER-3",
                new MessageEvents.SalesItem(5L, 5001L, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("productId=5");
    }

    @Test
    @DisplayName("空明细直接受理，避免毒消息无限重投")
    void acceptsEmptyItems() {
        StubSalesMapper mapper = new StubSalesMapper();
        ProductSalesService service = new ProductSalesService(mapper, new StubCache());

        assertThat(service.applyOrderPaid(event("ORDER-4"))).isTrue();
        assertThat(mapper.claims).isEmpty();
    }

    private static MessageEvents.OrderPaidEvent event(String orderNo, MessageEvents.SalesItem... items) {
        return new MessageEvents.OrderPaidEvent(orderNo, 10002L, "2026-11-05T10:00:00", List.of(items));
    }

    /** 手写桩：只模拟"认领唯一键 + 累加"两件事，便于断言调用次数。 */
    private static class StubSalesMapper implements ProductSalesMapper {

        final List<String> claims = new ArrayList<>();
        final List<Long> missingProducts = new ArrayList<>();
        int updateCalls;
        private final java.util.Map<Long, Integer> sales = new java.util.HashMap<>();

        @Override
        public int claim(String orderNo, Long productId, Integer quantity) {
            String key = orderNo + ":" + productId;
            if (claims.contains(key)) {
                throw new DuplicateKeyException("duplicate: " + key);
            }
            claims.add(key);
            return 1;
        }

        @Override
        public int addSales(Long productId, Integer quantity) {
            updateCalls++;
            if (missingProducts.contains(productId)) {
                return 0;
            }
            sales.merge(productId, quantity, Integer::sum);
            return 1;
        }

        int totalSales(Long productId) {
            return sales.getOrDefault(productId, 0);
        }
    }

    /** 缓存不参与断言：接一个全部 no-op 的桩即可。 */
    private static class StubCache extends MallCache {

        StubCache() {
            super(null, null, null);
        }

        @Override
        public void evict(String key) {
            // no-op
        }

        @Override
        public void evictNull(String key) {
            // no-op
        }
    }
}
