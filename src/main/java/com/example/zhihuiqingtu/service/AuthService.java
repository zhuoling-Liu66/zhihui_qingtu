package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.auth.LoginRequest;
import com.example.zhihuiqingtu.dto.auth.RegisterRequest;
import com.example.zhihuiqingtu.vo.auth.LoginResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;

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
}
