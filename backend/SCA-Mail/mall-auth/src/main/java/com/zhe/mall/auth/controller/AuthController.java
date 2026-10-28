package com.zhe.mall.auth.controller;

import com.zhe.mall.auth.dto.LoginRequest;
import com.zhe.mall.auth.dto.LoginVO;
import com.zhe.mall.auth.dto.RefreshRequest;
import com.zhe.mall.auth.dto.RegisterRequest;
import com.zhe.mall.auth.dto.RegisterVO;
import com.zhe.mall.auth.dto.UserInfoVO;
import com.zhe.mall.auth.service.AuthService;
import com.zhe.mall.common.auth.CurrentUserId;
import com.zhe.mall.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（契约见需求文档 26.4）。
 *
 * <p>register / login / refresh 在网关白名单内；logout / me 需要 accessToken。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final String FORWARDED_FOR_SEPARATOR = ",";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return Result.success(authService.login(request, resolveClientIp(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@CurrentUserId Long userId) {
        authService.logout(userId);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserInfoVO> me(@CurrentUserId Long userId) {
        return Result.success(authService.currentUser(userId));
    }

    /**
     * 取真实客户端 IP：优先 X-Forwarded-For 的第一段（网关透传），否则用连接地址。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(FORWARDED_FOR_SEPARATOR)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
