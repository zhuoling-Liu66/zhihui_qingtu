package com.example.zhihuiqingtu.dto.enrollment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 活动报名请求。
 */
@Data
public class CreateEnrollmentRequest {

    /**
     * 用户学号或工号。
     */
    @NotBlank(message = "用户不能为空")
    private String userId;

    /**
     * 活动ID。
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
