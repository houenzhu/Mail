package com.zhe.mall.user.controller;

import com.zhe.mall.api.dto.UserCreateRequest;
import com.zhe.mall.api.dto.UserDTO;
import com.zhe.mall.common.auth.AuthHeaders;
import com.zhe.mall.common.auth.CurrentUserId;
import com.zhe.mall.common.auth.InternalTokenVerifier;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.user.service.UserService;
import com.zhe.mall.user.vo.UserProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    private final InternalTokenVerifier internalTokenVerifier;

    public UserController(UserService userService, InternalTokenVerifier internalTokenVerifier) {
        this.userService = userService;
        this.internalTokenVerifier = internalTokenVerifier;
    }

    /**
     * 当前登录用户档案，身份取自网关注入的登录上下文（ADR-013）。
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> profile(@CurrentUserId Long userId) {
        return Result.success(userService.getProfile(userId));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getById(@PathVariable("id") Long id) {
        return Result.success(userService.getById(id));
    }

    /**
     * 内部接口：注册建档，仅允许携带内部令牌的调用方访问（网关同时拦截 /internal 路径的外部访问）。
     */
    @PostMapping("/internal/create")
    public Result<UserDTO> create(@RequestBody UserCreateRequest request,
                                  @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false)
                                  String internalToken) {
        internalTokenVerifier.verify(internalToken);
        return Result.success(userService.create(request));
    }
}