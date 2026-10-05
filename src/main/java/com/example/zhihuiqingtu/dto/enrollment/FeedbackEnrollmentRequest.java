package com.example.zhihuiqingtu.dto.enrollment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 活动评价请求。
 * <p>
 * 注意：<b>没有"评价人"字段</b>。评价人由服务端从 JWT 取当前登录用户
 * （见 {@code CurrentUser}），不接受客户端自报 —— 否则可以伪造成别人的学号去评价他人的报名记录。
 */
@Data
public class FeedbackEnrollmentRequest {

    /**
     * 活动评分。
     */
    @Min(value = 1, message = "评分不能低于1")
    @Max(value = 5, message = "评分不能高于5")
    private Integer feedbackScore;

    /**
     * 活动评价内容。
     */
    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "长度不能超过500")
    private String feedbackContent;
}
