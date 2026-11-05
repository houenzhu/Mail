package com.zhe.mall.user.mapper;

import com.zhe.mall.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAddressMapper {

    /** 默认地址排在前面，其余按更新时间倒序。 */
    List<UserAddress> selectByUserId(@Param("userId") Long userId);

    UserAddress selectById(@Param("id") Long id, @Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);

    int insert(UserAddress address);

    int update(UserAddress address);

    int clearDefault(@Param("userId") Long userId);

    int markDefault(@Param("id") Long id, @Param("userId") Long userId);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 删除默认地址后提升最近更新的一条（有则返回其 id）。 */
    UserAddress selectLatestByUserId(@Param("userId") Long userId);
}
