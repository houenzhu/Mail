package com.zhe.mall.product.mapper;

import com.zhe.mall.product.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类查询与维护。
 *
 * <p>说明：此前只有建表脚本而没有 Mapper —— 商品表有 {@code category_id}，但没有任何接口能列出分类，
 * 导致发布商品时分类下拉无数据（计划文档 G-08）。读能力已补齐；增删改随后台分类维护（M3）落地。
 */
@Mapper
public interface CategoryMapper {

    /** 全部启用分类，按层级与排序值返回（后台下拉与分类树共用）。 */
    List<Category> selectAll();

    Category selectById(@Param("id") Long id);

    int insert(Category category);

    /** 更新可编辑字段（parent_id/name/level/sort_no/status）；不动 deleted。 */
    int update(Category category);

    int softDelete(@Param("id") Long id);

    /**
     * 同父分类下按名称查重（对齐唯一键 {@code uk_parent_name(parent_id, name)}），并排除自身。
     *
     * <p>排除自身是必须的：编辑时未改名的分类若不排除自己，会直接被判为"名称已存在"。
     */
    int countByNameExcluding(@Param("parentId") Long parentId, @Param("name") String name,
                             @Param("excludeId") Long excludeId);

    /** 未删除的子分类数量（删除前校验，见 M3 计划「决策 5」）。 */
    int countChildByParentId(@Param("parentId") Long parentId);

    /** 引用该分类的未删除商品数量（跨表查 product，同库）。 */
    int countProductsByCategoryId(@Param("categoryId") Long categoryId);
}