package com.zhe.mall.cart.store;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 购物车容量与保留时长配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.cart")
public class CartProperties {

    /** 无操作后的保留时长，默认 30 天。 */
    private long ttlSeconds = 2_592_000L;

    /** 单个用户最多保留的 SKU 种类数。 */
    private int maxItems = 100;

    /** 单个 SKU 最大数量。 */
    private int maxQuantity = 99;
}
