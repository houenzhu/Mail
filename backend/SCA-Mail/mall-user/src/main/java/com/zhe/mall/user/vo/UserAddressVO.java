package com.zhe.mall.user.vo;

import java.time.LocalDateTime;

/** 地址视图：额外返回拼接后的完整地址，避免每个调用方各自拼字符串。 */
public record UserAddressVO(Long id, String receiverName, String receiverPhone,
                            String region, String detailAddress, String fullAddress,
                            Boolean isDefault, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
