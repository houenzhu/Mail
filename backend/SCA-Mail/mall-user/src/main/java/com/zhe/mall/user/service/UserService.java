package com.zhe.mall.user.service;

import com.zhe.mall.api.dto.UserCreateRequest;
import com.zhe.mall.api.dto.UserDTO;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.user.entity.User;
import com.zhe.mall.user.mapper.UserMapper;
import com.zhe.mall.user.vo.UserProfileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户档案服务。档案由 mall-auth 在注册时通过内部接口建立，本服务不处理口令与令牌（ADR-009）。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int DEFAULT_LEVEL = 1;

    private static final int DEFAULT_STATUS = 1;

    private static final String MASK_SEGMENT = "****";

    private static final int PHONE_PREFIX_LENGTH = 3;

    private static final int PHONE_SUFFIX_LENGTH = 4;

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserProfileVO getProfile(Long userId) {
        return toProfileVO(requireUser(userId));
    }

    public UserDTO getById(Long userId) {
        User user = requireUser(userId);
        return new UserDTO(user.getId(), user.getUsername(), user.getNickname(),
                maskPhone(user.getPhone()), user.getLevel());
    }

    /**
     * 内部建档：以 id 为主键幂等，重复调用（注册重试）返回已有档案，不覆盖既有数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDTO create(UserCreateRequest request) {
        if (request == null || request.id() == null || request.username() == null || request.username().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "建档缺少 id 或 username");
        }
        User existing = userMapper.selectById(request.id());
        if (existing != null) {
            log.info("建档幂等命中: userId={}", request.id());
            return new UserDTO(existing.getId(), existing.getUsername(), existing.getNickname(),
                    maskPhone(existing.getPhone()), existing.getLevel());
        }
        User user = new User();
        user.setId(request.id());
        user.setUsername(request.username());
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setPhone(request.phone());
        user.setLevel(DEFAULT_LEVEL);
        user.setGrowth(0);
        user.setPoints(0);
        user.setStatus(DEFAULT_STATUS);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // uk_username 冲突：同名档案已存在（不同 user_id），交由调用方处理
            log.warn("建档用户名冲突: userId={}, username={}", request.id(), request.username());
            throw new BusinessException(ResultCode.USERNAME_REGISTERED, "用户名已存在: " + request.username());
        }
        log.info("建档完成: userId={}, username={}", user.getId(), user.getUsername());
        return new UserDTO(user.getId(), user.getUsername(), user.getNickname(),
                maskPhone(user.getPhone()), user.getLevel());
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "userId 不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在: " + userId);
        }
        return user;
    }

    private UserProfileVO toProfileVO(User user) {
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(),
                maskPhone(user.getPhone()), user.getLevel(), user.getGrowth(), user.getPoints());
    }

    /**
     * 手机号脱敏（21.4）：保留前 3 位与后 4 位，中间固定 4 个星号。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < PHONE_PREFIX_LENGTH + PHONE_SUFFIX_LENGTH) {
            return phone;
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH) + MASK_SEGMENT
                + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }
}