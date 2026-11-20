package com.example.zhihuiqingtu.admin.auth.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.auth.dto.LoginRequest;
import com.example.zhihuiqingtu.auth.service.AuthService;
import com.example.zhihuiqingtu.auth.vo.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端独立登录入口。
 *
 * <p>与客户端 {@code /api/auth/login} 分开：只有管理员和运营人员能登录，
 * 同时运营人员不能从客户端登录，保证「管理端普通账号」是独立的后台账号。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    /**
     * 管理端登录。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", authService.loginAdmin(request));
    }
}
