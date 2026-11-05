package com.zhe.mall.user.controller;

import com.zhe.mall.common.auth.CurrentUserId;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.user.dto.UserAddressRequest;
import com.zhe.mall.user.service.UserAddressService;
import com.zhe.mall.user.vo.UserAddressVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址簿（UC-USER-02）。
 *
 * <p>路径挂在 {@code /api/user/address}，网关已有 {@code /api/user/**} 路由且不在白名单，
 * 因此自动要求登录；用户身份只取 {@code @CurrentUserId}，请求体里没有 userId。
 */
@RestController
@RequestMapping("/api/user/address")
public class UserAddressController {

    private final UserAddressService addressService;

    public UserAddressController(UserAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public Result<List<UserAddressVO>> list(@CurrentUserId Long userId) {
        return Result.success(addressService.list(userId));
    }

    @PostMapping
    public Result<UserAddressVO> create(@Valid @RequestBody UserAddressRequest request,
                                        @CurrentUserId Long userId) {
        return Result.success(addressService.create(request, userId));
    }

    @PutMapping("/{id}")
    public Result<UserAddressVO> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody UserAddressRequest request,
                                        @CurrentUserId Long userId) {
        return Result.success(addressService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, @CurrentUserId Long userId) {
        addressService.delete(id, userId);
        return Result.success(null);
    }

    @PutMapping("/{id}/default")
    public Result<UserAddressVO> setDefault(@PathVariable("id") Long id,
                                            @CurrentUserId Long userId) {
        return Result.success(addressService.setDefault(id, userId));
    }
}
