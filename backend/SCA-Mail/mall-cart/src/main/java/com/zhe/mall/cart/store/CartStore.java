package com.zhe.mall.cart.store;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 购物车存储抽象。
 *
 * <p>抽成接口的目的不是"未来换存储"，而是让业务规则（库存、上下架、金额）可以用内存实现做单测，
 * 不必为了验证一条数量上限去启动真实 Redis。
 */
public interface CartStore {

    /** 加购成功，返回累加后的数量。 */
    int RESULT_QUANTITY_EXCEEDED = -1;

    /** 超过可售库存。 */
    int RESULT_STOCK_NOT_ENOUGH = -2;

    /** 购物车 SKU 种类达到上限。 */
    int RESULT_ITEM_LIMIT = -3;

    /** 购物车中不存在该 SKU。 */
    int RESULT_NOT_FOUND = -4;

    /**
     * 原子加购：同一 SKU 累加数量，并校验单品上限、实时库存与购物车种类上限。
     *
     * @return 累加后的数量，或上面的负值错误码
     */
    int add(Long userId, Long skuId, int quantity, int maxQuantity, int stockLimit,
            int maxItems, Duration ttl);

    /**
     * 原子修改数量；quantity <= 0 表示删除。
     *
     * @return 新数量、0（删除成功）或负值错误码
     */
    int updateQuantity(Long userId, Long skuId, int quantity, int maxQuantity,
                       int stockLimit, Duration ttl);

    /** 删除 SKU；返回是否确实删除了行。 */
    boolean remove(Long userId, Long skuId, Duration ttl);

    /** 读取全部行（按加购顺序稳定排序）。 */
    List<StoredCartItem> items(Long userId, Duration ttl);

    Optional<StoredCartItem> find(Long userId, Long skuId);

    /**
     * 批量设置勾选状态。
     *
     * @param skuIds 为空表示全部
     * @return 实际发生变化的行数
     */
    int setSelected(Long userId, List<Long> skuIds, boolean selected, Duration ttl);
}
