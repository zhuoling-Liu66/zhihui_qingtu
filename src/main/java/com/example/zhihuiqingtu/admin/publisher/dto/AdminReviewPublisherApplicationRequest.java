package com.example.zhihuiqingtu.admin.publisher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端审批发布权限申请。
 */
@Data
public class AdminReviewPublisherApplicationRequest {

    /**
     * 审批结果：1 通过 / 2 驳回。
     */
    @NotNull(message = "请选择审批结果")
    @Min(value = 1, message = "审批结果仅支持通过或驳回")
    @Max(value = 2, message = "审批结果仅支持通过或驳回")
    private Integer recordStatus;

    /**
     * 驳回原因；驳回时必填。
     */
    @Size(max = 255, message = "驳回原因不能超过255字")
    private String rejectReason;
}
