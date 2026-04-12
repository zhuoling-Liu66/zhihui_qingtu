package com.example.zhihuiqingtu.dto.enrollment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报名审核请求。
 */
@Data
public class ReviewEnrollmentRequest {

    /**
     * 审核人学号或工号。
     */
    @NotBlank(message = "审核人不能为空")
    private String reviewUserId;

    /**
     * 审核结果状态。
     */
    @NotNull(message = "审核状态不能为空")
    private Integer enrollStatus;
}
