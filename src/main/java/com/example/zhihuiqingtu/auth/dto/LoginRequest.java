package com.example.zhihuiqingtu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    /**
     * 学号或工号。
     */
    @NotBlank(message = "不能为空")
    private String studentNo;

    /**
     * 密码。
     */
    @NotBlank(message = "不能为空")
    private String password;
}
