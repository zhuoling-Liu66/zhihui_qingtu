package com.example.zhihuiqingtu.dto.enrollment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 活动评价请求。
 */
@Data
public class FeedbackEnrollmentRequest {

    @NotBlank(message = "用户不能为空")
    private String userId;

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
