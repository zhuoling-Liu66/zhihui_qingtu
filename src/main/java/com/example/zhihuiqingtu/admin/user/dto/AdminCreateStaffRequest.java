package com.example.zhihuiqingtu.admin.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员创建运营账号的请求。
 */
@Data
public class AdminCreateStaffRequest {

    @NotBlank(message = "工号不能为空")
    @Size(max = 20, message = "工号长度不能超过20")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String userName;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String userPhone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String userEmail;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在6到50之间")
    private String password;
}
