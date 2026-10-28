package com.zhe.mall.product.mapper;

import com.zhe.mall.product.dto.ProductPageQuery;
import com.zhe.mall.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductMapper {

    Product selectById(@Param("id") Long id);

    /**
     * 分页查询。{@code query} 里带 deleted 与 productId 条件，因此管理端（含已删除/按 ID 精确查）
     * 与 C 端共用这一条语句，避免再写一套近乎重复的管理端查询。
     */
    List<Product> selectPage(@Param("query") ProductPageQuery query, @Param("offset") int offset, @Param("limit") int limit);

    long countPage(@Param("query") ProductPageQuery query);

    /** 按状态统计数量（管理端列表的 Tab 角标一次拿全，避免前端发 4 个 count 请求）。 */
    List<java.util.Map<String, Object>> countGroupByStatus(@Param("deleted") Integer deleted);

    int insert(Product product);

    /**
     * 更新商品主数据（编辑用）。
     *
     * <p>只更新可编辑字段：{@code name/subtitle/category_id/brand_id/main_image/detail/status}。
     * 刻意不碰 {@code sales}（由交易链路回写）、{@code deleted}（逻辑删除独立接口）、
     * {@code price_min/price_max}（由 {@link #updatePriceRange} 依据 SKU 重算），
     * 避免一次编辑把这些字段意外覆盖。
     */
    int update(Product product);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 逻辑删除（管理端）：只置 deleted，保留数据以便追溯。 */
    int softDelete(@Param("id") Long id);

    int updatePriceRange(@Param("id") Long id, @Param("priceMin") BigDecimal priceMin, @Param("priceMax") BigDecimal priceMax);
}
