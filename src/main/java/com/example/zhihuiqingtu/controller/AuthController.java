package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.dto.auth.LoginRequest;
import com.example.zhihuiqingtu.dto.auth.RegisterRequest;
import com.example.zhihuiqingtu.service.AuthService;
import com.example.zhihuiqingtu.vo.auth.LoginResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证模块控制器。
 * <p>
 * 负责注册、登录等与“身份认证”相关的接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * 认证业务服务。
     */
    private final AuthService authService;

    /**
     * 用户注册接口。
     *
     * @param request 注册请求体
     * @return 注册完成后的用户资料
     */
    @PostMapping("/register")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("注册成功", authService.register(request));
    }

    /**
     * 用户登录接口。
     *
     * @param request 登录请求体
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", authService.login(request));
    }

    /**
     * 用户登出接口。
     * <p>
     * 把当前令牌写入 Redis 黑名单，key 的 TTL = 令牌剩余有效期，
     * 从而让"无状态 JWT"具备主动失效能力（否则签出去的令牌只能等它自然过期）。
     * <p>
     * 该接口在 {@code SecurityConfig} 里未被放行，所以能进来就说明令牌一定合法
     * （签名有效且未过期），这里不需要重复校验。
     *
     * @param jwt 当前请求的 JWT —— 由 Spring Security 框架注入，
     *            不再手工从请求头取字符串。这是"用框架"的直接好处：
     *            身份相关的信息由框架解析好再交给我们，减少了手写出错的可能。
     * @return 空响应体
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        // 直接把框架解析好的 Jwt 交给 service：它内部用 jti 做黑名单 key、
        // 用 exp 计算 TTL，不需要再从原始字符串解析一遍。
        authService.logout(jwt);
        return ApiResponse.success("登出成功", null);
    }
}
