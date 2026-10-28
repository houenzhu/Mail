package com.zhe.mall.product.mapper;

import com.zhe.mall.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductSkuMapper {

    ProductSku selectById(@Param("id") Long id);

    List<ProductSku> selectByProductId(@Param("productId") Long productId);

    /**
     * 按商品 ID 批量查 SKU，返回**扁平列表**（调用方用 groupingBy 分组）。
     *
     * <p>刻意不用 MyBatis 的 {@code @MapKey}：它返回的是 {@code Map<productId, List<sku>>}，
     * 多一层嵌套且与列名强耦合；扁平列表 + 显式分组更直观，也不依赖列名。
     */
    List<ProductSku> selectByProductIds(@Param("productIds") Collection<Long> productIds);

    List<ProductSku> selectBySkuIds(@Param("skuIds") Collection<Long> skuIds);

    int countBySkuCode(@Param("skuCode") String skuCode);

    /**
     * 编码查重并**排除自身**（编辑商品用）。
     *
     * <p>{@link #countBySkuCode} 不排除自身，编辑时会对"没改编码的已有 SKU"误报"编码已存在"，
     * 因此更新路径必须用本方法逐个排除自身。
     */
    int countBySkuCodeExcluding(@Param("skuCode") String skuCode, @Param("excludeSkuId") Long excludeSkuId);

    int insertBatch(@Param("skus") List<ProductSku> skus);

    /**
     * 按主键原地更新 SKU（编辑商品用），**主键不变**。
     *
     * <p>不用"软删全部再重插"：{@code inventory.sku_id} 与订单明细都引用 SKU 主键，
     * 重建会让库存与历史订单全部对不上（见 M3 计划「决策 2」）。
     */
    int updateBatch(@Param("skus") List<ProductSku> skus);

    /** 逻辑删除指定的 SKU（编辑商品时请求里未出现的 SKU）。 */
    int softDeleteByIds(@Param("ids") Collection<Long> ids);
}
