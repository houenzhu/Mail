package com.zhe.mall.product.service;

import com.zhe.mall.api.dto.CategorySaveRequest;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.product.entity.Category;
import com.zhe.mall.product.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分类维护（管理端，M3）。
 *
 * <p>两条规则集中在服务端，避免客户端各写一遍：
 * <ol>
 *   <li><b>层级由父分类推导</b>：{@code parentId = 0} → 1 级，否则 {@code 父.level + 1}，
 *       并挡在 3 级以内（表注释约定 1-3 级）；</li>
 *   <li><b>名称在同父下唯一</b>（对齐唯一键 {@code uk_parent_name}），更新时排除自身。</li>
 * </ol>
 */
@Service
public class CategoryService {

    /** 顶级分类的 parentId 约定值（建表默认 0）。 */
    private static final long TOP_PARENT_ID = 0L;

    /** 分类最大层级（对齐建表注释「1 一级 2 二级 3 三级」）。 */
    private static final int MAX_LEVEL = 3;

    private static final int DEFAULT_LEVEL = 1;

    private static final int STATUS_ENABLED = 1;

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveRequest request) {
        long parentId = normalizeParentId(request.parentId());
        Category parent = parentId == TOP_PARENT_ID ? null : requireCategory(parentId);
        int level = parent == null ? DEFAULT_LEVEL : parent.getLevel() + 1;
        if (level > MAX_LEVEL) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分类层级最多 " + MAX_LEVEL + " 级");
        }
        if (categoryMapper.countByNameExcluding(parentId, request.name(), null) > 0) {
            throw new BusinessException(ResultCode.CATEGORY_NAME_DUPLICATED, "同级下分类名称已存在: " + request.name());
        }

        Category category = new Category();
        category.setParentId(parentId);
        category.setName(request.name());
        category.setLevel(level);
        category.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        category.setStatus(request.status() == null ? STATUS_ENABLED : request.status());
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long categoryId, CategorySaveRequest request) {
        Category existing = requireCategory(categoryId);
        long parentId = normalizeParentId(request.parentId());
        if (parentId == categoryId) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分类不能把自己设为父分类");
        }
        // 改父级会让整棵子树的 level 失真，而子孙层级不会跟着重算；
        // 有子分类时直接拒绝改父级（改名/排序/状态不受影响）。
        if (parentId != existing.getParentId() && categoryMapper.countChildByParentId(categoryId) > 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该分类下还有子分类，不能变更父级");
        }

        Category parent = parentId == TOP_PARENT_ID ? null : requireCategory(parentId);
        int level = parent == null ? DEFAULT_LEVEL : parent.getLevel() + 1;
        if (level > MAX_LEVEL) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分类层级最多 " + MAX_LEVEL + " 级");
        }
        if (categoryMapper.countByNameExcluding(parentId, request.name(), categoryId) > 0) {
            throw new BusinessException(ResultCode.CATEGORY_NAME_DUPLICATED, "同级下分类名称已存在: " + request.name());
        }

        Category toSave = new Category();
        toSave.setId(categoryId);
        toSave.setParentId(parentId);
        toSave.setName(request.name());
        toSave.setLevel(level);
        toSave.setSortNo(request.sortNo() == null ? existing.getSortNo() : request.sortNo());
        toSave.setStatus(request.status() == null ? existing.getStatus() : request.status());
        categoryMapper.update(toSave);
    }

    /**
     * 逻辑删除：先挡"被引用"。
     *
     * <p>删掉在用分类会让商品落在"看不见的分类"上（见 M3 计划「决策 5」），
     * 因此有未删除子分类或有商品引用时直接拒绝，并给出可读原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long categoryId) {
        requireCategory(categoryId);
        if (categoryMapper.countChildByParentId(categoryId) > 0) {
            throw new BusinessException(ResultCode.CATEGORY_IN_USE, "该分类下还有子分类，请先删除子分类");
        }
        int referenced = categoryMapper.countProductsByCategoryId(categoryId);
        if (referenced > 0) {
            throw new BusinessException(ResultCode.CATEGORY_IN_USE, "该分类下还有 " + referenced + " 个商品，无法删除");
        }
        categoryMapper.softDelete(categoryId);
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "分类不存在: " + categoryId);
        }
        return category;
    }

    private long normalizeParentId(Long parentId) {
        return parentId == null ? TOP_PARENT_ID : parentId;
    }
}