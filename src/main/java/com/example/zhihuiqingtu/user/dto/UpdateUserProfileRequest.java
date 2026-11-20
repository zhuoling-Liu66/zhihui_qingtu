package com.example.zhihuiqingtu.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求。
 */
@Data
public class UpdateUserProfileRequest {

    /**
     * 用户姓名。
     */
    @Size(max = 50, message = "长度不能超过50")
    private String userName;

    /**
     * 手机号。
     */
    @Size(max = 20, message = "长度不能超过20")
    private String userPhone;

    /**
     * 邮箱。
     */
    @Email(message = "格式不正确")
    @Size(max = 100, message = "长度不能超过100")
    private String userEmail;

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
