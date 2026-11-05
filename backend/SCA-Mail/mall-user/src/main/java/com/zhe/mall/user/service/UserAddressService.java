package com.zhe.mall.user.service;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.common.util.SnowflakeIdGenerator;
import com.zhe.mall.user.dto.UserAddressRequest;
import com.zhe.mall.user.entity.UserAddress;
import com.zhe.mall.user.mapper.UserAddressMapper;
import com.zhe.mall.user.vo.UserAddressVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址簿（UC-USER-02）。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li><b>归属只认登录态</b>：userId 来自网关注入的 {@code @CurrentUserId}，
 *       所有查询与更新都带 {@code and user_id = ?}，他人地址一律 31007（不暴露存在性）；</li>
 *   <li><b>默认唯一</b>：由生成列 {@code default_user_id} 的唯一索引保证，
 *       业务层先清旧默认再设新默认，并发撞索引时转成 31009 让前端刷新重试；</li>
 *   <li><b>删除默认要补位</b>：删默认地址时在同一事务里把最近更新的一条提为默认，
 *       否则结算页会拿不到默认地址。</li>
 * </ol>
 */
@Service
public class UserAddressService {

    private static final Logger log = LoggerFactory.getLogger(UserAddressService.class);

    private static final int DEFAULT_FLAG = 1;

    private static final int NOT_DEFAULT = 0;

    private final UserAddressMapper addressMapper;

    private final int maxPerUser;

    public UserAddressService(UserAddressMapper addressMapper,
                             @Value("${mall.user.address.max-per-user:20}") int maxPerUser) {
        this.addressMapper = addressMapper;
        this.maxPerUser = maxPerUser;
    }

    public List<UserAddressVO> list(Long userId) {
        requireLogin(userId);
        return addressMapper.selectByUserId(userId).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddressVO create(UserAddressRequest request, Long userId) {
        requireLogin(userId);
        int count = addressMapper.countByUserId(userId);
        if (count >= maxPerUser) {
            throw new BusinessException(ResultCode.ADDRESS_LIMIT_EXCEEDED,
                    "最多保存 " + maxPerUser + " 条收货地址");
        }
        // 第一条地址默认设默认，避免用户加完地址还要再点一次
        boolean makeDefault = request.defaultRequested() || count == 0;
        if (makeDefault) {
            addressMapper.clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setId(SnowflakeIdGenerator.nextId());
        address.setUserId(userId);
        applyRequest(address, request, makeDefault);
        try {
            addressMapper.insert(address);
        } catch (DuplicateKeyException e) {
            // 并发建首条地址：唯一索引 uk_default_user 命中
            throw new BusinessException(ResultCode.ADDRESS_DEFAULT_CONFLICT,
                    "默认地址已被其他请求设置，请刷新后重试");
        }
        log.info("新增收货地址: userId={}, addressId={}, isDefault={}",
                userId, address.getId(), makeDefault);
        return toVO(requireAddress(address.getId(), userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddressVO update(Long id, UserAddressRequest request, Long userId) {
        requireLogin(userId);
        UserAddress existing = requireAddress(id, userId);
        boolean makeDefault = request.defaultRequested();
        if (makeDefault) {
            addressMapper.clearDefault(userId);
        } else if (Integer.valueOf(DEFAULT_FLAG).equals(existing.getIsDefault())) {
            // 不允许把当前默认地址改成非默认：否则用户会处于"有地址但无默认"的状态，
            // 结算页仍需额外兜底。要取消默认请直接设另一条为默认。
            makeDefault = true;
        }
        UserAddress address = new UserAddress();
        address.setId(existing.getId());
        address.setUserId(userId);
        applyRequest(address, request, makeDefault);
        try {
            int affected = addressMapper.update(address);
            if (affected == 0) {
                throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND, "收货地址不存在或已删除");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ADDRESS_DEFAULT_CONFLICT,
                    "默认地址已被其他请求设置，请刷新后重试");
        }
        log.info("修改收货地址: userId={}, addressId={}, isDefault={}", userId, id, makeDefault);
        return toVO(requireAddress(id, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        requireLogin(userId);
        UserAddress existing = requireAddress(id, userId);
        int affected = addressMapper.softDelete(id, userId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND, "收货地址不存在或已删除");
        }
        if (Integer.valueOf(DEFAULT_FLAG).equals(existing.getIsDefault())) {
            // 删除默认地址后补位，保证「有地址则必有默认」
            UserAddress candidate = addressMapper.selectLatestByUserId(userId);
            if (candidate != null) {
                addressMapper.markDefault(candidate.getId(), userId);
                log.info("删除默认地址后补位: userId={}, promoted={}", userId, candidate.getId());
            }
        }
        log.info("删除收货地址: userId={}, addressId={}", userId, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddressVO setDefault(Long id, Long userId) {
        requireLogin(userId);
        requireAddress(id, userId);
        addressMapper.clearDefault(userId);
        try {
            int affected = addressMapper.markDefault(id, userId);
            if (affected == 0) {
                throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND, "收货地址不存在或已删除");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ADDRESS_DEFAULT_CONFLICT,
                    "默认地址已被其他请求设置，请刷新后重试");
        }
        log.info("设置默认地址: userId={}, addressId={}", userId, id);
        return toVO(requireAddress(id, userId));
    }

    private void applyRequest(UserAddress address, UserAddressRequest request, boolean makeDefault) {
        address.setReceiverName(request.receiverName().trim());
        address.setReceiverPhone(request.receiverPhone().trim());
        String region = request.region() == null ? "" : request.region().trim();
        address.setRegion(region);
        address.setDetailAddress(request.detailAddress().trim());
        address.setIsDefault(makeDefault ? DEFAULT_FLAG : NOT_DEFAULT);
    }

    private UserAddress requireAddress(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "地址 ID 不能为空");
        }
        UserAddress address = addressMapper.selectById(id, userId);
        if (address == null) {
            throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND, "收货地址不存在");
        }
        return address;
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    private UserAddressVO toVO(UserAddress address) {
        return new UserAddressVO(address.getId(), address.getReceiverName(), address.getReceiverPhone(),
                address.getRegion(), address.getDetailAddress(), fullAddress(address),
                Integer.valueOf(DEFAULT_FLAG).equals(address.getIsDefault()),
                address.getCreatedAt(), address.getUpdatedAt());
    }

    /** 完整地址：region 为空时只返回详细地址，避免拼出以空格开头的字符串。 */
    private String fullAddress(UserAddress address) {
        String region = address.getRegion() == null ? "" : address.getRegion().trim();
        String detail = address.getDetailAddress() == null ? "" : address.getDetailAddress().trim();
        return region.isEmpty() ? detail : region + " " + detail;
    }
}
