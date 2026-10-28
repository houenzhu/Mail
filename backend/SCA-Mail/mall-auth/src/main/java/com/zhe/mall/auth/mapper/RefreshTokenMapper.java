package com.zhe.mall.auth.mapper;

import com.zhe.mall.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefreshTokenMapper {

    RefreshToken selectByJti(@Param("jti") String jti);

    int insert(RefreshToken refreshToken);

    int revoke(@Param("jti") String jti, @Param("rotatedTo") String rotatedTo);

    int revokeAllByUser(@Param("userId") Long userId);
}
