package com.zhe.mall.product.mapper;

import com.zhe.mall.product.entity.Brand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 品牌查询与维护（与 {@link CategoryMapper} 同一背景：此前只有表没有 Mapper，见计划文档 G-08）。
 */
@Mapper
public interface BrandMapper {

    /** 全部启用品牌。 */
    List<Brand> selectAll();

    Brand selectById(@Param("id") Long id);

    int insert(Brand brand);

    /** 更新可编辑字段（name/logo_url/description/sort_no/status）；不动 deleted。 */
    int update(Brand brand);

    int softDelete(@Param("id") Long id);

    /** 全局按名称查重（对齐唯一键 {@code uk_name(name)}），并排除自身。 */
    int countByNameExcluding(@Param("name") String name, @Param("excludeId") Long excludeId);

    /** 引用该品牌的未删除商品数量（跨表查 product，同库）。 */
    int countProductsByBrandId(@Param("brandId") Long brandId);
}