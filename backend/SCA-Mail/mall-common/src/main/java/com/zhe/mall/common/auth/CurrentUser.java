package com.zhe.mall.common.auth;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户，来自网关透传的身份头。
 */
public record CurrentUser(Long userId, String username, List<String> roles) {

    /** 后台角色集合：客服 / 运营 / 管理员（见需求文档 16.1）。 */
    private static final Set<String> STAFF_ROLES =
            Set.of(UserRole.CS.name(), UserRole.OPERATOR.name(), UserRole.ADMIN.name());

    /**
     * 角色的包含关系：管理员的权限覆盖运营与客服。
     *
     * <p>我们的角色是"账号级单一职责标签"（`user_account.roles` 存逗号分隔的角色），
     * 例如种子账号 `admin` 只有 ADMIN、`operator01` 只有 OPERATOR。
     * 如果做严格相等匹配，`@RequireStaff(UserRole.OPERATOR)` 会把管理员挡在门外——
     * 而"管理员能管商品"是业务常识，也让后台出现"最高权限反而不能操作"的荒谬结果。
     *
     * <p>因此这里集中定义一次包含关系，避免在每个调用点各写一套判断。
     */
    public boolean hasRole(String role) {
        if (role == null || roles == null || roles.isEmpty()) {
            return false;
        }
        if (roles.contains(role)) {
            return true;
        }
        // ADMIN 覆盖 OPERATOR 与 CS
        if (roles.contains(UserRole.ADMIN.name())) {
            return UserRole.OPERATOR.name().equals(role) || UserRole.CS.name().equals(role);
        }
        // OPERATOR 覆盖 CS
        return roles.contains(UserRole.OPERATOR.name()) && UserRole.CS.name().equals(role);
    }

    /**
     * 客服 / 运营 / 管理员可跨用户操作。
     */
    public boolean isStaff() {
        return roles != null && roles.stream().anyMatch(STAFF_ROLES::contains);
    }
}
