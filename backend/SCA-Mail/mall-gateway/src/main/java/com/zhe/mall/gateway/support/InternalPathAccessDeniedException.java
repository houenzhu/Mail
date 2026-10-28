package com.zhe.mall.gateway.support;

/**
 * 外部请求命中了 {@code /internal/} 路径（见需求文档 21.2 / 26.9）。
 *
 * <p>用于让鉴权过滤器把该情形与"未登录"区分开，分别返回 403 与 401。
 */
public class InternalPathAccessDeniedException extends RuntimeException {

    public InternalPathAccessDeniedException(String path) {
        super("内部接口不允许外部访问: " + path);
    }
}