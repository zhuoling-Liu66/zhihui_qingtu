package com.example.zhihuiqingtu.admin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置运营账号密码的请求。
 */
@Data
public class AdminResetPasswordRequest {

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在6到50之间")
    private String password;
}
