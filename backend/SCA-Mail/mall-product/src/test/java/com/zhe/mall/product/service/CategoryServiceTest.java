package com.zhe.mall.product.service;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.product.entity.Category;
import com.zhe.mall.product.mapper.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryService#delete(Long)} 的「被引用拦截」规则测试。
 *
 * <p>这是 M3 的删除安全闸：分类一旦被删，引用它的商品会落到「看不见的分类」上
 * （列表按分类筛不到、发布页下拉里也没有），因此有子分类或有商品引用时必须拒绝。
 * 这两条只有在库里有真实引用数据时才会触发，纯前端 / 手工点测很容易漏掉，
 * 所以在这里用桩把两条分支都钉死。
 */
class CategoryServiceTest {

    private static final Long CATEGORY_ID = 9L;

    private CategoryMapper categoryMapper;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        service = new CategoryService(categoryMapper);

        Category existing = new Category();
        existing.setId(CATEGORY_ID);
        existing.setParentId(0L);
        existing.setName("手机数码");
        existing.setLevel(1);
        existing.setStatus(1);
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(existing);
    }

    @Test
    @DisplayName("分类下还有子分类：拒绝删除，且不落库")
    void rejectsDeleteWhenChildExists() {
        when(categoryMapper.countChildByParentId(CATEGORY_ID)).thenReturn(2);

        assertThatThrownBy(() -> service.delete(CATEGORY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CATEGORY_IN_USE.getCode());

        verify(categoryMapper, never()).softDelete(CATEGORY_ID);
    }

    @Test
    @DisplayName("分类已被商品引用：拒绝删除，且不落库")
    void rejectsDeleteWhenReferencedByProduct() {
        when(categoryMapper.countChildByParentId(CATEGORY_ID)).thenReturn(0);
        when(categoryMapper.countProductsByCategoryId(CATEGORY_ID)).thenReturn(5);

        assertThatThrownBy(() -> service.delete(CATEGORY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CATEGORY_IN_USE.getCode());

        verify(categoryMapper, never()).softDelete(CATEGORY_ID);
    }

    @Test
    @DisplayName("无子分类且无商品引用：正常逻辑删除")
    void deletesWhenUnused() {
        when(categoryMapper.countChildByParentId(CATEGORY_ID)).thenReturn(0);
        when(categoryMapper.countProductsByCategoryId(CATEGORY_ID)).thenReturn(0);

        service.delete(CATEGORY_ID);

        verify(categoryMapper).softDelete(CATEGORY_ID);
    }
}