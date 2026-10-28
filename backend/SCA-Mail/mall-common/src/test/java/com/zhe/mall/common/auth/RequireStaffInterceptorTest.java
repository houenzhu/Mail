package com.zhe.mall.common.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequireStaffInterceptor} 的行为测试。
 *
 * <p>这是安全边界代码（防后台接口越权，见需求文档 21.3 / 计划文档 G-01），
 * 因此逐条覆盖"该放行的放行、该拒绝的拒绝"，而不是只测一条 happy path。
 *
 * <p>身份来源说明：{@code UserContext} 由 {@code UserContextFilter} 写入，
 * 而该过滤器只在请求携带正确内部令牌时才采信身份头（fail-closed，ADR-012）。
 * 因此这里直接构造 {@code UserContext} 来模拟"网关已认证并透传身份"的场景，
 * 并单独用"上下文为空"覆盖直连服务/伪造身份头的情形。
 */
class RequireStaffInterceptorTest {

    private final RequireStaffInterceptor interceptor = new RequireStaffInterceptor();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未标注 @RequireStaff 的接口一律放行（不影响 C 端与普通接口）")
    void allowsEndpointWithoutAnnotation() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request(), response, handlerMethod("publicEndpoint"));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("运营访问后台接口：放行")
    void allowsOperator() throws Exception {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of(UserRole.OPERATOR.name())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(), response, handlerMethod("adminEndpoint"));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("管理员访问后台接口：放行（ADMIN 命中 OPERATOR 要求）")
    void allowsAdminWhenOperatorRequired() throws Exception {
        UserContext.set(new CurrentUser(10001L, "admin", List.of(UserRole.ADMIN.name())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request(), response, handlerMethod("adminEndpoint"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("买家访问后台接口：403 + 与网关同形的错误体（修复越权缺陷 G-01）")
    void rejectsBuyerWith403() throws Exception {
        UserContext.set(new CurrentUser(10002L, "buyer01", List.of(UserRole.BUYER.name())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(), response, handlerMethod("adminEndpoint"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString())
                .contains("\"code\":20003")
                .contains("无后台访问权限")
                .contains("\"success\":false");
    }

    @Test
    @DisplayName("无身份（直连服务/伪造身份头）：401，不误放行")
    void rejectsAnonymousWith401() throws Exception {
        UserContext.clear();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(), response, handlerMethod("adminEndpoint"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("\"code\":20001");
    }

    @Test
    @DisplayName("角色不匹配（要求 ADMIN 但只有 OPERATOR）：403")
    void rejectsWhenRequiredRoleNotHeld() throws Exception {
        UserContext.set(new CurrentUser(10003L, "operator01", List.of(UserRole.OPERATOR.name())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(), response, handlerMethod("adminOnlyEndpoint"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("客服访问运营专属接口：403（包含关系是 ADMIN⊇OPERATOR⊇CS，不是反向）")
    void rejectsCsWhenOperatorRequired() throws Exception {
        UserContext.set(new CurrentUser(10004L, "cs01", List.of(UserRole.CS.name())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(), response, handlerMethod("adminEndpoint"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("控制器类级 @RequireStaff 对未标注的方法同样生效")
    void appliesClassLevelAnnotation() throws Exception {
        UserContext.set(new CurrentUser(10004L, "cs01", List.of(UserRole.CS.name())));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request(), response, handlerMethodOn(ClassLevelAdmin.class, "any"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);

        UserContext.set(new CurrentUser(10002L, "buyer01", List.of(UserRole.BUYER.name())));
        MockHttpServletResponse denied = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request(), denied, handlerMethodOn(ClassLevelAdmin.class, "any"))).isFalse();
        assertThat(denied.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("非 HandlerMethod（静态资源等）不参与校验")
    void ignoresNonHandlerMethod() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request(), response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /* ---------------- 测试用句柄 ---------------- */

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/product");
        return request;
    }

    private HandlerMethod handlerMethod(String name) throws Exception {
        return handlerMethodOn(SampleController.class, name);
    }

    private HandlerMethod handlerMethodOn(Class<?> type, String name) throws Exception {
        Method method = type.getMethod(name);
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), method);
    }

    /** 模拟一个普通控制器：部分方法要求后台角色，部分不要求。 */
    static class SampleController {

        public void publicEndpoint() {
        }

        @RequireStaff(UserRole.OPERATOR)
        public void adminEndpoint() {
        }

        @RequireStaff(UserRole.ADMIN)
        public void adminOnlyEndpoint() {
        }
    }

    /** 模拟类级标注：该类下所有接口都要求后台角色。 */
    @RequireStaff
    static class ClassLevelAdmin {

        public void any() {
        }
    }
}
