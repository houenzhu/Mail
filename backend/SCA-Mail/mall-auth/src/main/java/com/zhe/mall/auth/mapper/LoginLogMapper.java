package com.zhe.mall.auth.mapper;

import com.zhe.mall.auth.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper {

    int insert(LoginLog loginLog);
}
