package com.zhe.mall.user.service;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.user.dto.UserAddressRequest;
import com.zhe.mall.user.entity.UserAddress;
import com.zhe.mall.user.mapper.UserAddressMapper;
import com.zhe.mall.user.vo.UserAddressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收货地址簿（UC-USER-02）单测。
 *
 * <p>重点覆盖：
 * <ul>
 *   <li>首条地址自动设默认；已有默认时新增非默认不再清默认；</li>
 *   <li>设置默认先清旧默认（默认唯一由 DB 生成列唯一索引兜底）；</li>
 *   <li>删除默认地址后自动补位，删除非默认不补位；</li>
 *   <li>他人地址 / 已删除地址一律 31007，不泄露存在性；</li>
 *   <li>数量上限返回 31008；未登录返回 20001。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock
    private UserAddressMapper addressMapper;

    private UserAddressService addressService;

    private static final Long USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        // 构造器第二参是 ${mall.user.address.max-per-user}，单测直接给 20；
        // 上限用例再通过 ReflectionTestUtils 覆盖，避免为了测试加一个默认构造器。
        addressService = new UserAddressService(addressMapper, 20);
    }

    private void limit(int value) {
        ReflectionTestUtils.setField(addressService, "maxPerUser", value);
    }

    private UserAddressRequest request(boolean isDefault) {
        return new UserAddressRequest("张三", "13800000000", "广东省广州市天河区",
                "珠江新城 1 号", isDefault);
    }

    private UserAddress address(long id, int isDefault) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(USER_ID);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setRegion("广东省广州市天河区");
        address.setDetailAddress("珠江新城 1 号");
        address.setIsDefault(isDefault);
        address.setDeleted(0);
        return address;
    }

    @Test
    @DisplayName("首条地址自动设为默认，并先清空旧默认")
    void firstAddressBecomesDefault() {
        limit(20);
        when(addressMapper.countByUserId(USER_ID)).thenReturn(0);
        when(addressMapper.selectById(anyLong(), eq(USER_ID))).thenReturn(address(1L, 1));

        UserAddressVO vo = addressService.create(request(false), USER_ID);

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).clearDefault(USER_ID);
        verify(addressMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isEqualTo(1);
        assertThat(vo.isDefault()).isTrue();
    }

    @Test
    @DisplayName("已有地址时新增非默认，不清除现有默认")
    void secondAddressKeepsDefault() {
        limit(20);
        when(addressMapper.countByUserId(USER_ID)).thenReturn(1);
        when(addressMapper.selectById(anyLong(), eq(USER_ID))).thenReturn(address(2L, 0));

        addressService.create(request(false), USER_ID);

        verify(addressMapper, never()).clearDefault(anyLong());
        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isZero();
    }

    @Test
    @DisplayName("只有登录用户能访问，userId 为空返回 20001")
    void requiresLogin() {
        assertThatThrownBy(() -> addressService.list(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("地址数量达到上限返回 31008")
    void rejectsOverLimit() {
        limit(2);
        when(addressMapper.countByUserId(USER_ID)).thenReturn(2);

        assertThatThrownBy(() -> addressService.create(request(false), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.ADDRESS_LIMIT_EXCEEDED.getCode());
    }

    @Test
    @DisplayName("设置默认先清旧默认，再标记目标地址")
    void setDefaultClearsPrevious() {
        when(addressMapper.selectById(5L, USER_ID)).thenReturn(address(5L, 1));
        when(addressMapper.markDefault(5L, USER_ID)).thenReturn(1);
        // 首次 requireAddress 与返回前的复查彼此独立，按顺序返回两次
        when(addressMapper.selectById(5L, USER_ID)).thenReturn(address(5L, 1));

        addressService.setDefault(5L, USER_ID);

        verify(addressMapper).clearDefault(USER_ID);
        verify(addressMapper).markDefault(5L, USER_ID);
    }

    @Test
    @DisplayName("删除默认地址后把最近一条提为默认")
    void deleteDefaultPromotesLatest() {
        when(addressMapper.selectById(7L, USER_ID)).thenReturn(address(7L, 1));
        when(addressMapper.softDelete(7L, USER_ID)).thenReturn(1);
        when(addressMapper.selectLatestByUserId(USER_ID)).thenReturn(address(8L, 0));

        addressService.delete(7L, USER_ID);

        verify(addressMapper).markDefault(8L, USER_ID);
    }

    @Test
    @DisplayName("删除非默认地址不触发补位")
    void deleteNonDefaultSkipsPromotion() {
        when(addressMapper.selectById(7L, USER_ID)).thenReturn(address(7L, 0));
        when(addressMapper.softDelete(7L, USER_ID)).thenReturn(1);

        addressService.delete(7L, USER_ID);

        verify(addressMapper, never()).selectLatestByUserId(anyLong());
        verify(addressMapper, never()).markDefault(anyLong(), anyLong());
    }

    @Test
    @DisplayName("他人地址按不存在处理（31007），不泄露存在性")
    void othersAddressIsNotFound() {
        when(addressMapper.selectById(9L, USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> addressService.setDefault(9L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.ADDRESS_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("完整地址由 region + 详细地址拼接，region 为空时不产生前导空格")
    void fullAddressConcatenation() {
        when(addressMapper.selectByUserId(USER_ID)).thenReturn(List.of(address(1L, 1)));

        List<UserAddressVO> list = addressService.list(USER_ID);
        assertThat(list.get(0).fullAddress()).isEqualTo("广东省广州市天河区 珠江新城 1 号");

        UserAddress noRegion = address(2L, 0);
        noRegion.setRegion(null);
        when(addressMapper.selectByUserId(USER_ID)).thenReturn(List.of(noRegion));
        assertThat(addressService.list(USER_ID).get(0).fullAddress()).isEqualTo("珠江新城 1 号");
    }

    @Test
    @DisplayName("当前默认地址更新时不能被改为非默认，避免出现「有地址无默认」")
    void currentDefaultCannotBeUnset() {
        when(addressMapper.selectById(3L, USER_ID)).thenReturn(address(3L, 1));
        when(addressMapper.update(any(UserAddress.class))).thenReturn(1);

        addressService.update(3L, request(false), USER_ID);

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).update(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isEqualTo(1);
    }
}
