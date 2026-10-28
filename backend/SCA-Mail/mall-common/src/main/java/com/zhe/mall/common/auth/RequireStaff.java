package com.zhe.mall.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「仅后台人员可访问」的接口或控制器（见需求文档 16.1 / 21.3，ADR-013）。
 *
 * <p>默认放行 客服(CS) / 运营(OPERATOR) / 管理员(ADMIN)，即 {@link CurrentUser#isStaff()}；
 * 需要更严的角色时用 {@link #value()} 指定，命中任一即可。
 *
 * <p><b>为什么需要这个注解</b>：以往后台能力靠"路径前缀 {@code /api/admin/**}"约定 + 网关鉴权，
 * 但网关只校验"是否登录"，不校验角色，于是任何登录用户（如买家）都能调用商品新增、上下架等写接口。
 * 本注解把角色校验前移到服务端方法级，从接口上消除越权面（与 ADR-013 同一思路）。
 *
 * <p><b>fail-closed</b>：校验依据是网关注入的 {@code X-User-Id} / {@code X-User-Role}，
 * 而身份头只在请求携带正确内部令牌时才会被 {@link UserContextFilter} 采信；
 * 未配置内部令牌或直连服务伪造身份头，一律视为匿名 → 401，不会误放行。
 *
 * <pre>{@code
 * @PostMapping
 * @RequireStaff                       // 客服/运营/管理员
 * public Result<Long> create(@RequestBody ProductCreateRequest request) { ... }
 *
 * @PutMapping("/{id}/status")
 * @RequireStaff(UserRole.OPERATOR)    // 仅运营与管理员
 * public Result<Void> updateStatus(...) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireStaff {

    /**
     * 允许的角色；留空表示"任意后台角色"（{@link CurrentUser#isStaff()}）。
     */
    UserRole[] value() default {};
}
