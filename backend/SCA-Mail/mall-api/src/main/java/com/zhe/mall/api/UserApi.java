package com.zhe.mall.api;

import com.zhe.mall.api.dto.UserCreateRequest;
import com.zhe.mall.api.dto.UserDTO;
import com.zhe.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mall-user", path = "/api/user")
public interface UserApi {

    @GetMapping("/{id}")
    Result<UserDTO> getById(@PathVariable("id") Long id);

    /**
     * 内部接口：注册时建立用户档案，按 id 幂等。网关禁止外部访问 /internal 路径（见 21.2）。
     */
    @PostMapping("/internal/create")
    Result<UserDTO> create(@RequestBody UserCreateRequest request);
}