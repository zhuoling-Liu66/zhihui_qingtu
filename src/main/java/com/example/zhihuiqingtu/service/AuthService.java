package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.auth.LoginRequest;
import com.example.zhihuiqingtu.dto.auth.RegisterRequest;
import com.example.zhihuiqingtu.vo.auth.LoginResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 认证业务服务接口。
 */
public interface AuthService {

    /**
     * 注册用户。
     *
     * @param request 注册请求参数
     * @return 注册成功后的用户资料
     */
    UserProfileResponse register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求参数
     * @return 登录结果
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出：把当前令牌加入黑名单，使其在剩余有效期内立即失效。
     * <p>
     * 之所以需要服务端参与，是因为 JWT 无状态 —— 前端删掉本地 token 并不能让它在服务端失效。
     * <p>
     * 参数收 {@link Jwt} 而不是原始字符串：令牌已由 Spring Security 校验并解析，
     * 直接从中取 {@code jti} 与 {@code exp} 即可，不必再解析一次。
     *
     * @param jwt 当前请求的 JWT（由框架注入）
     */
    void logout(Jwt jwt);
}
