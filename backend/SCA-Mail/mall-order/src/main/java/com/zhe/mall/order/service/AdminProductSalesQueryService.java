package com.zhe.mall.order.service;

import com.zhe.mall.api.dto.AdminProductSalesStat;
import com.zhe.mall.order.mapper.OrderItemMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台商品销量的订单侧聚合（G-13）。
 *
 * <p>商品与订单分属两个库，不能跨库 JOIN，因此 30 日销量只能由订单服务按
 * {@code product_id} 聚合后交给 BFF 合并（与库存聚合同一套路数）。
 *
 * <p>时区与看板一致：固定 {@code Asia/Shanghai}，不随 JVM 默认时区漂移；
 * 时间窗为「今天往前 N 天的 00:00:00」到「明天 00:00:00」。
 */
@Service
public class AdminProductSalesQueryService {

    /** 与看板一致：最多 90 天，防止异常参数扫全表。 */
    private static final int MAX_DAYS = 90;

    static final int DEFAULT_DAYS = 30;

    private final OrderItemMapper orderItemMapper;

    public AdminProductSalesQueryService(OrderItemMapper orderItemMapper) {
        this.orderItemMapper = orderItemMapper;
    }

    /**
     * 批量查询商品近 N 日销量。
     *
     * @param productIds 商品 ID 列表；为空直接返回空列表，不发 SQL（避免 {@code in ()} 语法错误）
     * @param days       天数，收敛到 1~90，默认 30
     */
    public List<AdminProductSalesStat> sales(List<Long> productIds, Integer days) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = productIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        int safeDays = days == null ? DEFAULT_DAYS : Math.max(1, Math.min(MAX_DAYS, days));
        LocalDate today = LocalDate.now(AdminOrderDashboardService.BIZ_ZONE);
        LocalDateTime start = today.minusDays(safeDays - 1L).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        return orderItemMapper.selectProductSalesBetween(distinct, start, end);
    }
}
