package com.zhe.mall.auth.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpLogic;
import com.zhe.mall.auth.config.AuthProperties;
import com.zhe.mall.auth.entity.RefreshToken;
import com.zhe.mall.auth.mapper.RefreshTokenMapper;
import com.zhe.mall.common.cache.MallCacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 刷新令牌（见需求文档 21.8.7 / ADR-025）。
 *
 * <p>由 Sa-Token 以独立 loginType {@code refresh} 签发：网关只校验默认的 {@code login}，
 * 因此刷新令牌天然不能当访问令牌使用（对齐 ADR-010）。在线判定完全以 Sa-Token 会话为准，
 * 表 {@code mall_auth.refresh_token} 只承担审计留痕。
 *
 * <p><b>不落原始令牌</b>：审计表存 SHA-256 指纹前 32 位（与表上 CHAR(32) 对齐），避免库泄漏即会话泄漏。
 *
 * <p>重放检测：Sa-Token 旋转后旧令牌直接失效，无法再区分"无效"与"重放"，
 * 故旋转时额外写一条短 TTL 标记 {@code mall:auth:refresh-rotated:{指纹}} 来保留 26.6 的能力。
 */
@Component
public class RefreshTokenManager {

    /** 刷新令牌的 Sa-Token loginType */
    public static final String LOGIN_TYPE = "refresh";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenManager.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final int FINGERPRINT_LENGTH = 32;

    private static final String MARKER_SEPARATOR = "|";

    private static final String HASH_ALGORITHM = "SHA-256";

    private final StpLogic stpLogic;

    private final RefreshTokenMapper refreshTokenMapper;

    private final long ttlSeconds;

    /** 允许为 null：未接 Redis 时重放检测退化为"仅拒绝"（不影响主流程） */
    private final StringRedisTemplate redis;

    public RefreshTokenManager(AuthProperties properties,
                               RefreshTokenMapper refreshTokenMapper,
                               ObjectProvider<StringRedisTemplate> redisProvider,
                               @Value("${sa-token.token-name:satoken}") String tokenName) {
        this.ttlSeconds = properties.getRefreshTtlSeconds();
        this.refreshTokenMapper = refreshTokenMapper;
        this.redis = redisProvider.getIfAvailable();
        SaTokenConfig config = new SaTokenConfig()
                .setTokenName(tokenName)
                .setTokenStyle("uuid")
                .setTimeout(ttlSeconds)
                .setActiveTimeout(-1)
                .setAutoRenew(false)
                .setIsConcurrent(true)
                .setIsShare(false)
                .setIsReadCookie(false)
                .setIsLastingCookie(false)
                .setIsWriteHeader(false)
                .setIsLog(false);
        this.stpLogic = new StpLogic(LOGIN_TYPE).setConfig(config);
        SaManager.putStpLogic(this.stpLogic);
        log.info("刷新令牌逻辑初始化: loginType={}, ttl={}s, tokenName={}", LOGIN_TYPE, ttlSeconds, tokenName);
    }

    /** 签发刷新令牌并写审计。 */
    public String issue(Long userId) {
        String token = stpLogic.createLoginSession(userId);
        auditInsert(token, userId, Instant.now(), Instant.now().plusSeconds(ttlSeconds));
        return token;
    }

    /** 令牌对应的用户 ID；无效 / 已过期 / 已被踢出均返回 null。 */
    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Object loginId = stpLogic.getLoginIdByTokenNotThinkFreeze(token);
            if (loginId == null) {
                return null;
            }
            return Long.valueOf(loginId.toString());
        } catch (SaTokenException e) {
            return null;
        }
    }

    /**
     * 旋转：作废旧令牌、写下重放标记、签发新令牌。
     *
     * <p>顺序很关键：先签发新令牌，再作废旧令牌，避免"作废成功但签发失败"把用户锁在门外。
     */
    public String rotate(String oldToken, Long userId) {
        String newToken = issue(userId);
        markRotated(oldToken, userId, newToken);
        try {
            stpLogic.logoutByTokenValue(oldToken);
        } catch (SaTokenException e) {
            log.warn("作废旧刷新令牌失败（新令牌已签发）: error={}", e.getMessage());
        }
        auditRevoke(oldToken, fingerprint(newToken));
        return newToken;
    }

    /** 该令牌是否为"已被旋转过"的重放；是则返回其所属用户 ID，否则 null。 */
    public Long rotatedUserId(String token) {
        if (redis == null || token == null || token.isBlank()) {
            return null;
        }
        try {
            String marker = redis.opsForValue().get(MallCacheKeys.authRefreshRotated(fingerprint(token)));
            if (marker == null || marker.isBlank()) {
                return null;
            }
            int separator = marker.indexOf(MARKER_SEPARATOR);
            String userIdPart = separator < 0 ? marker : marker.substring(0, separator);
            return Long.valueOf(userIdPart.trim());
        } catch (RuntimeException e) {
            log.warn("读取刷新令牌重放标记失败（视为非重放）: error={}", e.getMessage());
            return null;
        }
    }

    /** 作废该用户全部刷新令牌（注销 / 重放处置）。 */
    public void revokeAll(Long userId) {
        try {
            stpLogic.kickout(userId);
        } catch (SaTokenException e) {
            log.warn("作废刷新令牌失败: userId={}, error={}", userId, e.getMessage());
        }
        try {
            int revoked = refreshTokenMapper.revokeAllByUser(userId);
            log.warn("强制下线：撤销刷新令牌 {} 条, userId={}", revoked, userId);
        } catch (RuntimeException e) {
            log.warn("审计撤销刷新令牌失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void markRotated(String oldToken, Long userId, String newToken) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(MallCacheKeys.authRefreshRotated(fingerprint(oldToken)),
                    userId + MARKER_SEPARATOR + fingerprint(newToken),
                    Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            log.warn("写刷新令牌重放标记失败（重放检测退化为直接拒绝）: error={}", e.getMessage());
        }
    }

    private void auditInsert(String token, Long userId, Instant issuedAt, Instant expiresAt) {
        try {
            RefreshToken row = new RefreshToken();
            row.setJti(fingerprint(token));
            row.setUserId(userId);
            row.setIssuedAt(LocalDateTime.ofInstant(issuedAt, ZONE));
            row.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZONE));
            row.setRevoked(0);
            refreshTokenMapper.insert(row);
        } catch (RuntimeException e) {
            log.warn("刷新令牌审计写入失败（不影响签发）: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void auditRevoke(String oldToken, String newFingerprint) {
        try {
            refreshTokenMapper.revoke(fingerprint(oldToken), newFingerprint);
        } catch (RuntimeException e) {
            log.warn("刷新令牌旋转审计失败（不影响主流程）: error={}", e.getMessage());
        }
    }

    /** 令牌指纹：SHA-256 十六进制前 32 位，与审计表 CHAR(32) 对齐。 */
    private String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            String hex = HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 " + HASH_ALGORITHM, e);
        }
    }
}
