package com.zhe.mall.auth.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.zhe.mall.api.UserApi;
import com.zhe.mall.api.dto.UserCreateRequest;
import com.zhe.mall.api.dto.UserDTO;
import com.zhe.mall.auth.config.AuthProperties;
import com.zhe.mall.auth.dto.LoginRequest;
import com.zhe.mall.auth.dto.LoginVO;
import com.zhe.mall.auth.dto.RegisterRequest;
import com.zhe.mall.auth.dto.RegisterVO;
import com.zhe.mall.auth.dto.UserInfoVO;
import com.zhe.mall.auth.entity.LoginLog;
import com.zhe.mall.auth.entity.UserAccount;
import com.zhe.mall.auth.enums.AccountStatus;
import com.zhe.mall.auth.enums.LoginResult;
import com.zhe.mall.auth.mapper.UserAccountMapper;
import com.zhe.mall.auth.support.Masker;
import com.zhe.mall.auth.support.RefreshTokenManager;
import com.zhe.mall.auth.support.PhoneHash;
import com.zhe.mall.common.auth.AuthSessionKeys;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.common.util.PasswordPolicy;
import com.zhe.mall.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 认证服务：注册、登录、刷新、注销（见需求文档 26.2 ~ 26.6）。
 *
 * <p>账号凭证只落 `mall_auth`；注册时先建档案（`mall_user`）再落账号，避免出现"有账号无档案"（26.6）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final String ROLE_SEPARATOR = ",";

    private static final String DEFAULT_ROLE = "USER";

    private static final String TOKEN_TYPE_BEARER = "Bearer";

    /** 读不到 {@code sa-token.timeout} 时的兜底有效期（21.1：accessToken 300s） */
    private static final long DEFAULT_ACCESS_TTL_SECONDS = 300L;

    private static final int USERNAME_MAX_LENGTH = 32;

    private static final int CLIENT_IP_MAX_LENGTH = 64;

    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final UserAccountMapper userAccountMapper;

    private final AuthAuditService authAuditService;

    private final RefreshTokenManager refreshTokenManager;

    private final UserApi userApi;

    private final PasswordEncoder passwordEncoder;

    private final AuthProperties properties;

    public AuthService(UserAccountMapper userAccountMapper,
                       AuthAuditService authAuditService,
                       RefreshTokenManager refreshTokenManager,
                       UserApi userApi,
                       PasswordEncoder passwordEncoder,
                       AuthProperties properties) {
        this.userAccountMapper = userAccountMapper;
        this.authAuditService = authAuditService;
        this.refreshTokenManager = refreshTokenManager;
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * 注册：校验唯一性 → 建档案（幂等）→ 落账号凭证。
     */
    @Transactional(rollbackFor = Exception.class)
    public RegisterVO register(RegisterRequest request) {
        String username = request.username().trim();
        String phone = request.phone().trim();
        String violation = PasswordPolicy.describeViolation(request.password());
        if (violation != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, violation);
        }
        if (userAccountMapper.selectByUsername(username) != null) {
            throw new BusinessException(ResultCode.USERNAME_REGISTERED, "用户名已存在: " + username);
        }
        String phoneHash = PhoneHash.of(phone, properties.getPhoneSalt());
        if (userAccountMapper.selectByPhoneHash(phoneHash) != null) {
            throw new BusinessException(ResultCode.PHONE_REGISTERED, "手机号已注册: " + Masker.phone(phone));
        }

        long userId = SnowflakeIdGenerator.nextId();
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? username : request.nickname().trim();
        createProfile(userId, username, nickname, phone);

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setUsername(username);
        account.setPhone(phone);
        account.setPhoneHash(phoneHash);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setNickname(nickname);
        account.setRoles(DEFAULT_ROLE);
        account.setStatus(AccountStatus.NORMAL.code());
        account.setFailCount(0);
        try {
            userAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            log.warn("注册并发冲突: username={}, userId={}", username, userId);
            if (e.getMessage() != null && e.getMessage().contains("uk_phone_hash")) {
                throw new BusinessException(ResultCode.PHONE_REGISTERED, "手机号已注册: " + Masker.phone(phone));
            }
            throw new BusinessException(ResultCode.USERNAME_REGISTERED, "用户名已存在: " + username);
        }
        log.info("注册成功: userId={}, username={}", userId, username);
        return new RegisterVO(userId, username, nickname);
    }

    /**
     * 登录：锁定判定 → 口令校验（失败计数与锁定）→ 签发令牌对。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request, String clientIp, String userAgent) {
        String username = request.username().trim();
        LocalDateTime now = LocalDateTime.now(ZONE);
        UserAccount account = userAccountMapper.selectByUsername(username);
        if (account == null) {
            recordAttempt(null, username, clientIp, userAgent, LoginResult.ACCOUNT_NOT_FOUND, null, null, null);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "账号或密码错误");
        }
        if (isStatus(account, AccountStatus.DISABLED)) {
            recordAttempt(account.getUserId(), username, clientIp, userAgent, LoginResult.DISABLED, null, null, null);
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (isLocked(account, now)) {
            recordAttempt(account.getUserId(), username, clientIp, userAgent, LoginResult.LOCKED, null, null, null);
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                    "账号已被锁定，请于 " + account.getLockUntil() + " 后重试");
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            int failCount = (account.getFailCount() == null ? 0 : account.getFailCount()) + 1;
            boolean locked = failCount >= properties.getLoginFailLock().getMaxFailCount();
            LocalDateTime lockUntil = locked
                    ? now.plusMinutes(properties.getLoginFailLock().getLockMinutes()) : null;
            Integer status = locked ? AccountStatus.LOCKED.code() : AccountStatus.NORMAL.code();
            recordAttempt(account.getUserId(), username, clientIp, userAgent,
                    LoginResult.PASSWORD_ERROR, failCount, status, lockUntil);
            if (locked) {
                recordAttempt(account.getUserId(), username, clientIp, userAgent,
                        LoginResult.LOCKED, null, null, null);
                log.warn("登录失败次数超限，账号锁定: username={}, failCount={}", username, failCount);
                throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                        "密码错误次数过多，账号已锁定 " + properties.getLoginFailLock().getLockMinutes() + " 分钟");
            }
            throw new BusinessException(ResultCode.PASSWORD_ERROR,
                    "账号或密码错误，还可尝试 " + (properties.getLoginFailLock().getMaxFailCount() - failCount) + " 次");
        }
        userAccountMapper.updateLoginSuccess(account.getUserId(), now);
        account.setLastLoginAt(now);
        recordAttempt(account.getUserId(), username, clientIp, userAgent, LoginResult.SUCCESS, null, null, null);
        log.info("登录成功: userId={}, username={}, ip={}", account.getUserId(), username, clientIp);
        return issueTokenPair(account);
    }

    /**
     * 刷新：旋转刷新令牌（旧令牌立即作废）→ 重建访问会话。
     *
     * <p><b>重放检测</b>：Sa-Token 以会话为准，旋转后旧令牌直接失效，无法再区分"无效"与"重放"，
     * 故 {@link RefreshTokenManager} 在旋转时额外写一条 TTL 与刷新令牌一致的标记；命中即视为疑似泄露，
     * 强制下线该用户全部会话（26.6）。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：重放处置与审计落库发生在"写完就抛业务异常"的路径上，
     * 包在同一个事务里会被异常一并回滚（26.8 第 2 条）。本方法没有业务写，失败分支的落库必须独立提交。
     */
    public LoginVO refresh(String refreshTokenValue) {
        Long replayedUserId = refreshTokenManager.rotatedUserId(refreshTokenValue);
        if (replayedUserId != null) {
            terminateAllSessions(replayedUserId);
            log.warn("检测到已被旋转的刷新令牌被重放，已强制下线该用户: userId={}", replayedUserId);
            throw new BusinessException(ResultCode.TOKEN_EXPIRED, "刷新令牌已失效，请重新登录");
        }
        Long userId = refreshTokenManager.resolveUserId(refreshTokenValue);
        if (userId == null) {
            log.warn("刷新令牌无效、已过期或已被踢出");
            throw new BusinessException(ResultCode.TOKEN_EXPIRED, "刷新令牌无效，请重新登录");
        }
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!isStatus(account, AccountStatus.NORMAL)) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        return issueTokens(account, refreshTokenManager.rotate(refreshTokenValue, userId));
    }

    /**
     * 注销：全端下线 —— 作废该用户**全部**访问会话与刷新令牌（21.9.3 / ADR-026）。
     *
     * <p>迁移前的注销只在黑名单登记"当前 accessToken 的 jti"，其它设备的令牌不受影响，
     * 语义是"阻断续期"；改由 Sa-Token 承担后，{@code StpUtil.logout(userId)} 会删掉该账号的全部
     * 令牌索引，所有设备的 accessToken 立即失效，语义升级为"立即下线"。
     */
    public void logout(Long userId) {
        terminateAllSessions(userId);
        log.info("注销成功，访问会话与刷新令牌均已作废: userId={}", userId);
    }

    /**
     * 全端下线：作废该用户全部访问会话与刷新令牌。
     *
     * <p>访问令牌与刷新令牌走两个独立的 Sa-Token loginType，各自维护令牌索引，
     * 必须都清 —— 只清刷新令牌的话，"强制下线"仅挡住续期，已签发的 accessToken
     * 在剩余 TTL（≤300s）内仍然可用。
     */
    private void terminateAllSessions(Long userId) {
        StpUtil.logout(userId);
        refreshTokenManager.revokeAll(userId);
    }

    public UserInfoVO currentUser(Long userId) {
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在: " + userId);
        }
        return new UserInfoVO(account.getUserId(), account.getUsername(), account.getNickname(),
                Masker.phone(account.getPhone()), splitRoles(account.getRoles()),
                account.getStatus(), account.getLastLoginAt());
    }

    /**
     * 登录后签发令牌对：新刷新令牌 + 新访问会话。
     */
    private LoginVO issueTokenPair(UserAccount account) {
        return issueTokens(account, refreshTokenManager.issue(account.getUserId()));
    }

    /**
     * 签发访问令牌（Sa-Token 账号会话）并组装返回体；{@code refreshToken} 由调用方给出。
     *
     * <p>用户名与角色写入<b>账号会话</b>（{@link AuthSessionKeys}），网关校验令牌后从这里读出来注入
     * {@code X-User-Name} / {@code X-User-Role}（21.2 / 21.8.7）。按 {@code is-concurrent=true}
     * 同一账号允许多端并存，"强制下线"统一走 {@code kickout}。
     */
    private LoginVO issueTokens(UserAccount account, String refreshToken) {
        List<String> roles = splitRoles(account.getRoles());
        StpUtil.login(account.getUserId());
        SaSession session = StpUtil.getSession();
        session.set(AuthSessionKeys.USERNAME, account.getUsername());
        session.set(AuthSessionKeys.ROLES, String.join(ROLE_SEPARATOR, roles));
        return new LoginVO(account.getUserId(), account.getUsername(), account.getNickname(), roles,
                TOKEN_TYPE_BEARER, StpUtil.getTokenValue(), accessTokenTtlSeconds(),
                refreshToken, properties.getRefreshTtlSeconds());
    }

    /**
     * 访问令牌有效期：直接取 {@code sa-token.timeout}（唯一配置源，避免与 yml 漂移）；
     * 读不到时兜底 300s（21.1）。
     */
    private long accessTokenTtlSeconds() {
        SaTokenConfig config = SaManager.getConfig();
        return config == null || config.getTimeout() <= 0 ? DEFAULT_ACCESS_TTL_SECONDS : config.getTimeout();
    }

    /**
     * 建档：Feign 先于账号落库，失败则注册整体失败，不产生"有账号无档案"的脏数据。
     */
    private void createProfile(long userId, String username, String nickname, String phone) {
        Result<UserDTO> result;
        try {
            result = userApi.create(new UserCreateRequest(userId, username, nickname, phone));
        } catch (Exception e) {
            log.error("调用用户服务建档失败: userId={}, username={}", userId, username, e);
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "用户服务不可用，注册失败");
        }
        if (result == null) {
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "用户服务无响应，注册失败");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(result.code(), result.message());
        }
    }

    private boolean isStatus(UserAccount account, AccountStatus expected) {
        return account.getStatus() != null && account.getStatus() == expected.code();
    }

    private boolean isLocked(UserAccount account, LocalDateTime now) {
        if (!isStatus(account, AccountStatus.LOCKED)) {
            return false;
        }
        return account.getLockUntil() != null && account.getLockUntil().isAfter(now);
    }

    /**
     * 记录一次登录尝试。写入走独立事务（{@link AuthAuditService}），
     * 否则失败分支抛出业务异常时会把计数与日志一起回滚。
     */
    private void recordAttempt(Long userId, String username, String clientIp, String userAgent,
                              LoginResult result, Integer failCount, Integer status, LocalDateTime lockUntil) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(truncate(username, USERNAME_MAX_LENGTH));
            loginLog.setLoginIp(truncate(clientIp, CLIENT_IP_MAX_LENGTH));
            loginLog.setUserAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH));
            loginLog.setResult(result.code());
            if (failCount == null) {
                authAuditService.recordLoginAttempt(loginLog);
            } else {
                authAuditService.recordLoginFailure(loginLog, failCount, status, lockUntil);
            }
        } catch (Exception e) {
            log.warn("写登录日志失败: username={}, result={}", username, result.label(), e);
        }
    }

    private List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of(DEFAULT_ROLE);
        }
        return Arrays.stream(roles.split(ROLE_SEPARATOR)).map(String::trim).filter(role -> !role.isEmpty()).toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
