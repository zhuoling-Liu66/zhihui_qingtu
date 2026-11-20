package com.example.zhihuiqingtu.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 活动报名请求。
 * <p>
 * 注意：<b>没有"报名人"字段</b>。报名人由服务端从 JWT 取当前登录用户
 * （见 {@code CurrentUser}），因为身份一旦可以由客户端声明，就等于允许任意越权：
 * 原来那个 {@code userId} 是请求体自报的，任何人填上别人的学号，
 * 就能以别人的名义报名占名额。
 */
@Data
public class CreateEnrollmentRequest {

    /**
     * 活动ID。
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
