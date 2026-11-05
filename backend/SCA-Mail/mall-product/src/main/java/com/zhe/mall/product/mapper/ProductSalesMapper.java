package com.zhe.mall.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 累计销量回写（G-12）。
 *
 * <p>幂等由 {@code product_sales_record(order_no, product_id)} 唯一键保证：
 * 先抢认领行，插入成功才累加 {@code product.sales}；重复投递撞唯一键后直接跳过，
 * 不会重复加销量。选择"认领表 + 累加"而不是只靠 MQ 的 {@code consumed_message}：
 * 消息表只能保证同一 msgId 不重复，而订单可能被重新投递成新 msgId（补发、人工重放），
 * 业务唯一键才是最终防线。
 */
@Mapper
public interface ProductSalesMapper {

    /**
     * 认领一条销量回写记录。
     *
     * @return 1 表示首次认领，可以累加；0 表示重复事件
     */
    @Insert("""
            insert into product_sales_record (order_no, product_id, quantity)
            values (#{orderNo}, #{productId}, #{quantity})
            """)
    int claim(@Param("orderNo") String orderNo,
              @Param("productId") Long productId,
              @Param("quantity") Integer quantity);

    /** 累加累计销量；只更新未删除商品。 */
    @Update("""
            update product
            set sales = sales + #{quantity}
            where id = #{productId} and deleted = 0
            """)
    int addSales(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
