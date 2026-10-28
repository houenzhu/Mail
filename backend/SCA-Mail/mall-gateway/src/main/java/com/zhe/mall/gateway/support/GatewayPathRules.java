package com.zhe.mall.gateway.support;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.List;

/**
 * 网关路径规则：内部接口拦截与白名单匹配（口径与需求文档 21.2 / 27.6 一致，迁移 Sa-Token 后未变）。
 */
public final class GatewayPathRules {

    /** 内部接口路径标记：出现在路径中即视为内部接口，外部请求一律拦截（21.2 / 26.9） */
    private static final String INTERNAL_PATH_MARKER = "/internal/";

    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private GatewayPathRules() {
    }

    /** 内部接口路径：路径中出现 /internal/ 段（如 /api/user/internal/create）。 */
    public static boolean isInternalPath(String path) {
        return path != null && path.contains(INTERNAL_PATH_MARKER);
    }

    /**
     * 白名单匹配，规则格式 {@code METHOD:/路径}（省略 METHOD 表示放行所有方法）。
     */
    public static boolean isWhitelisted(List<String> whitelist, String method, String path) {
        if (whitelist == null || method == null || path == null) {
            return false;
        }
        for (String rule : whitelist) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            String trimmed = rule.trim();
            int separator = trimmed.indexOf(':');
            if (separator > 0) {
                String ruleMethod = trimmed.substring(0, separator).trim();
                String pattern = trimmed.substring(separator + 1).trim();
                if (ruleMethod.equalsIgnoreCase(method) && PATH_MATCHER.match(pattern, path)) {
                    return true;
                }
            } else if (PATH_MATCHER.match(trimmed, path)) {
                return true;
            }
        }
        return false;
    }
}