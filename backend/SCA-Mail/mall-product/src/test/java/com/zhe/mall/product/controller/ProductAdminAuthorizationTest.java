package com.zhe.mall.product.controller;

import com.zhe.mall.api.dto.AdminProductPageQuery;
import com.zhe.mall.common.auth.RequireStaff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理读取接口的服务端授权契约，防止后续重构时误删注解。 */
class ProductAdminAuthorizationTest {

    @Test
    @DisplayName("商品管理读取接口必须要求 staff")
    void productAdminReadsRequireStaff() throws Exception {
        assertThat(annotation(ProductController.class, "adminPage", AdminProductPageQuery.class)).isNotNull();
        assertThat(annotation(ProductController.class, "adminStatusCounts")).isNotNull();
        assertThat(annotation(ProductController.class, "adminSkuBatch", List.class)).isNotNull();
        assertThat(annotation(ProductController.class, "adminSkuByIds", List.class)).isNotNull();
    }

    @Test
    @DisplayName("管理分类和品牌读取接口必须要求 staff")
    void catalogAdminReadsRequireStaff() throws Exception {
        assertThat(annotation(CatalogAdminController.class, "listCategories")).isNotNull();
        assertThat(annotation(CatalogAdminController.class, "listBrands")).isNotNull();
    }

    private RequireStaff annotation(Class<?> type, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        return method.getAnnotation(RequireStaff.class);
    }
}
