package com.example.zhihuiqingtu.vo.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 用户唯一标识，值为学号或工号。
     */
    private String userId;

    /**
     * 学号或工号。
     */
    private String studentNo;

    /**
     * 用户姓名。
     */
    private String userName;

    /**
     * 用户角色。
     */
    private Integer userRole;

    /**
     * 发布权限状态。
     */
    private Integer publisherStatus;

    /**
     * 专业。
     */
    private String major;

    /**
     * 年级。
     */
    private String grade;

    /**
     * 访问令牌，用于后续请求认证。
     */
    private String token;
}
