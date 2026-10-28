package com.zhe.mall.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置（见需求文档 21.2 / 22.9）。
 *
 * <p>白名单是 YAML 列表，必须用 {@code @ConfigurationProperties} 绑定；{@code @Value} 无法绑定列表。
 */
@ConfigurationProperties(prefix = "mall.auth")
public class GatewayAuthProperties {

    /** 鉴权开关，关闭时仅透传 traceId（仅限本地调试） */
    private boolean enabled = true;

    /** 身份头可信证明，与各服务 mall.auth.internal-token 一致 */
    private String internalToken;

    /** 白名单，格式 METHOD:/路径（省略 METHOD 表示放行所有方法） */
    private List<String> whitelist = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
