package com.example.zhihuiqingtu.admin.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员审核活动请求。
 */
@Data
public class AdminReviewActivityRequest {

    /**
     * 审核结论：1 通过 / 2 驳回。
     */
    @NotNull(message = "请选择审核结论")
    private Integer reviewStatus;

    /**
     * 驳回原因或审核备注。
     */
    @Size(max = 255, message = "审核说明长度不能超过255")
    private String reviewReason;
}
