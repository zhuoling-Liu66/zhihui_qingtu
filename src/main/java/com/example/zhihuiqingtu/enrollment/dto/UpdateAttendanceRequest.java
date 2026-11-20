package com.example.zhihuiqingtu.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发布者/管理员修正考勤的请求。
 */
@Data
public class UpdateAttendanceRequest {

    /**
     * 操作：MARK_SIGN_IN / MARK_SIGN_OUT / REVERT_SIGN_IN / REVERT_SIGN_OUT。
     */
    @NotBlank(message = "考勤操作不能为空")
    private String action;
}
