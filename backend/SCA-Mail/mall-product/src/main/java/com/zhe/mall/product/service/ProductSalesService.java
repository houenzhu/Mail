package com.zhe.mall.product.service;

import com.zhe.mall.api.event.MessageEvents;
import com.zhe.mall.common.cache.MallCache;
import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.product.mapper.ProductSalesMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 累计销量回写（G-12）。
 *
 * <p>口径：<b>支付成功即计入销量</b>，按订单明细的 {@code qty} 累加到商品维度。
 * 退款暂不回减（本切片边界见计划文档），因此这里的 {@code sales} 语义是"累计成交件数"，
 * 与看板 GMV 口径（已支付及之后状态）保持一致。
 *
 * <p>同一订单里同一商品可能有多个 SKU，先按 productId 合并数量再累加，
 * 避免对同一行执行多次 UPDATE（也减少锁竞争）。
 */
@Service
public class ProductSalesService {

    private static final Logger log = LoggerFactory.getLogger(ProductSalesService.class);

    private final ProductSalesMapper salesMapper;

    private final MallCache mallCache;

    public ProductSalesService(ProductSalesMapper salesMapper, MallCache mallCache) {
        this.salesMapper = salesMapper;
        this.mallCache = mallCache;
    }

    /**
     * 处理订单已支付事件。
     *
     * @return true 表示已处理（含幂等跳过）；false 交给 MQ 重投
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyOrderPaid(MessageEvents.OrderPaidEvent event) {
        if (event == null || event.orderNo() == null || event.orderNo().isBlank()) {
            log.error("订单已支付事件缺少 orderNo，已丢弃");
            return true;
        }
        if (event.items() == null || event.items().isEmpty()) {
            log.warn("订单已支付事件没有明细，跳过销量回写: orderNo={}", event.orderNo());
            return true;
        }

        Map<Long, Integer> qtyByProduct = mergeQuantity(event.items());
        int applied = 0;
        int skipped = 0;
        for (Map.Entry<Long, Integer> entry : qtyByProduct.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            if (productId == null || productId <= 0 || quantity == null || quantity <= 0) {
                log.warn("销量明细不合法，跳过: orderNo={}, productId={}, qty={}",
                        event.orderNo(), productId, quantity);
                continue;
            }
            try {
                salesMapper.claim(event.orderNo(), productId, quantity);
            } catch (DuplicateKeyException e) {
                skipped++;
                log.debug("销量已回写，幂等跳过: orderNo={}, productId={}", event.orderNo(), productId);
                continue;
            }
            int affected = salesMapper.addSales(productId, quantity);
            if (affected == 0) {
                // 认领行已插入，但商品不存在/已删除：不能静默成功，否则会永久丢掉这笔销量。
                // 抛出异常让事务回滚（认领行一起消失）并由 MQ 重投；直到商品恢复或人工处理。
                log.error("商品不存在或已删除，销量回写回滚等待重投: orderNo={}, productId={}",
                        event.orderNo(), productId);
                throw new IllegalStateException("商品不存在或已删除: productId=" + productId);
            }
            applied++;
            evictDetailCache(productId);
        }
        log.info("销量回写完成: orderNo={}, products={}, applied={}, skipped={}",
                event.orderNo(), qtyByProduct.size(), applied, skipped);
        return true;
    }

    private Map<Long, Integer> mergeQuantity(List<MessageEvents.SalesItem> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (MessageEvents.SalesItem item : items) {
            if (item == null) {
                continue;
            }
            int qty = item.quantity() == null ? 0 : item.quantity();
            merged.merge(item.productId(), qty, Integer::sum);
        }
        return merged;
    }

    /**
     * 失效商品详情缓存：销量是详情的一部分，不清缓存会让 C 端在 TTL 内看到旧销量。
     *
     * <p>这里不在事务提交后清理：本方法是"幂等认领 + 累加"，重投时认领会跳过，
     * 若回滚则 sales 也回滚，缓存清空最多导致一次回源，没有脏数据风险。
     */
    private void evictDetailCache(Long productId) {
        mallCache.evict(MallCacheKeys.productDetail(productId));
        mallCache.evictNull(MallCacheKeys.productDetail(productId));
    }
}
