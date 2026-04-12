package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.auth.LoginRequest;
import com.example.zhihuiqingtu.dto.auth.RegisterRequest;
import com.example.zhihuiqingtu.vo.auth.LoginResponse;
import com.example.zhihuiqingtu.vo.user.UserProfileResponse;

/**
 * 认证服务接口。
 */
public interface AuthService {

    UserProfileResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
