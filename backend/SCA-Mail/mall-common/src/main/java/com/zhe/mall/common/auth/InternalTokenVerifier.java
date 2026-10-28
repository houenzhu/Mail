package com.zhe.mall.common.auth;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口调用凭据校验（见需求文档 21.2 / 26.9 / 27.3）。
 *
 * <p>网关会把客户端传入的 {@code X-Gateway-Token} 剥离后再注入内部令牌，并拦截含 {@code /internal/} 路径的
 * 外部访问；服务之间直连时也必须带上同一令牌，避免内网横向越权。
 *
 * <p><b>支持新旧双令牌并行（关闭 D-05）</b>：轮换时先下发新令牌 + 保留旧令牌（{@code -previous}），
 * 服务端两者都接受，待全部实例生效后再移除旧令牌，从而做到轮换期间不中断、不重启。
 */
public class InternalTokenVerifier {

    private final String currentToken;

    private final String previousToken;

    public InternalTokenVerifier(String currentToken) {
        this(currentToken, null);
    }

    public InternalTokenVerifier(String currentToken, String previousToken) {
        this.currentToken = currentToken;
        this.previousToken = previousToken;
    }

    /** 凭据是否已配置；未配置时服务端按 fail-closed 处理（不采信任何身份头）。 */
    public boolean configured() {
        return isPresent(currentToken) || isPresent(previousToken);
    }

    public void verify(String presentedToken) {
        if (!configured()) {
            throw new IllegalStateException("mall.auth.internal-token 未配置，内部接口不可用");
        }
        if (matches(currentToken, presentedToken) || matches(previousToken, presentedToken)) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "内部接口调用凭据无效");
    }

    /** 是否为可信调用方（不抛异常版本，供过滤器使用）。 */
    public boolean trusted(String presentedToken) {
        if (!configured()) {
            return false;
        }
        return matches(currentToken, presentedToken) || matches(previousToken, presentedToken);
    }

    /** 凭据指纹（仅取后 6 位），供启动日志确认"是否已下发"，禁止打印明文。 */
    public String fingerprint() {
        if (isPresent(currentToken)) {
            String value = currentToken;
            return value.length() <= 6 ? "******" : "******" + value.substring(value.length() - 6);
        }
        return "未配置";
    }

    private boolean isPresent(String token) {
        return token != null && !token.isBlank();
    }

    private boolean matches(String expected, String presented) {
        return isPresent(expected) && presented != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}