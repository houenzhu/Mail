package com.zhe.mall.auth.mapper;

import com.zhe.mall.auth.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserAccountMapper {

    UserAccount selectById(@Param("userId") Long userId);

    UserAccount selectByUsername(@Param("username") String username);

    UserAccount selectByPhoneHash(@Param("phoneHash") String phoneHash);

    int insert(UserAccount account);

    int updateLoginSuccess(@Param("userId") Long userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int updateLoginFail(@Param("userId") Long userId, @Param("failCount") Integer failCount,
                        @Param("status") Integer status, @Param("lockUntil") LocalDateTime lockUntil);
}
