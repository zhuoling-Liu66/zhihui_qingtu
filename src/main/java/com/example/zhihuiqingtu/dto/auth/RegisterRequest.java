package com.example.zhihuiqingtu.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求。
 */
@Data
public class RegisterRequest {

    /**
     * 学号或工号。
     */
    @NotBlank(message = "不能为空")
    @Size(max = 20, message = "长度不能超过20")
    private String studentNo;

    /**
     * 用户姓名。
     */
    @NotBlank(message = "不能为空")
    @Size(max = 50, message = "长度不能超过50")
    private String userName;

    /**
     * 用户手机号。
     */
    @Size(max = 20, message = "长度不能超过20")
    private String userPhone;

    /**
     * 用户邮箱。
     */
    @Email(message = "格式不正确")
    @Size(max = 100, message = "长度不能超过100")
    private String userEmail;

    /**
     * 用户角色。
     */
//    @NotNull(message = "不能为空")
//    private Integer userRole;

    /**
     * 密码明文。
     */
    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 50, message = "长度需在6到50之间")
    private String password;

    /**
     * 专业。
     */
    @Size(max = 100, message = "长度不能超过100")
    private String major;

    /**
     * 年级。
     */
    @Size(max = 20, message = "长度不能超过20")
    private String grade;
}
