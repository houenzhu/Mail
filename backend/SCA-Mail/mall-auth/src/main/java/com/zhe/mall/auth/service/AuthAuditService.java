package com.zhe.mall.auth.service;

import com.zhe.mall.auth.entity.LoginLog;
import com.zhe.mall.auth.mapper.LoginLogMapper;
import com.zhe.mall.auth.mapper.RefreshTokenMapper;
import com.zhe.mall.auth.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证审计与风控写入。
 *
 * <p>这些写入必须**独立事务**（{@code REQUIRES_NEW}）：登录失败、账号锁定、重放强制下线都会在写完数据后
 * 抛出业务异常，如果与业务同事务，写入会被一起回滚（失败计数永远停在 0、日志丢失、强制下线失效）。
 */
@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final UserAccountMapper userAccountMapper;

    private final LoginLogMapper loginLogMapper;

    private final RefreshTokenMapper refreshTokenMapper;

    public AuthAuditService(UserAccountMapper userAccountMapper,
                            LoginLogMapper loginLogMapper,
                            RefreshTokenMapper refreshTokenMapper) {
        this.userAccountMapper = userAccountMapper;
        this.loginLogMapper = loginLogMapper;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    /**
     * 记录一次失败尝试；{@code failCount} 不为空时同时更新失败计数与锁定状态。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordLoginFailure(LoginLog loginLog, Integer failCount, Integer status, LocalDateTime lockUntil) {
        if (failCount != null && loginLog.getUserId() != null) {
            userAccountMapper.updateLoginFail(loginLog.getUserId(), failCount, status, lockUntil);
        }
        loginLogMapper.insert(loginLog);
    }

    /**
     * 记录一次登录尝试（成功 / 未找到账号等不需要改计数的情况）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordLoginAttempt(LoginLog loginLog) {
        loginLogMapper.insert(loginLog);
    }

    /**
     * 检测到刷新令牌被重放时强制下线：撤销该用户全部刷新令牌。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void revokeAllRefreshTokens(Long userId) {
        int revoked = refreshTokenMapper.revokeAllByUser(userId);
        log.warn("强制下线：撤销刷新令牌 {} 条, userId={}", revoked, userId);
    }
}