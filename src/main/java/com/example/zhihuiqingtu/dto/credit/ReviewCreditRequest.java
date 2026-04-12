package com.example.zhihuiqingtu.dto.credit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学分审核请求。
 */
@Data
public class ReviewCreditRequest {

    /**
     * 审核人学号或工号。
     */
    @NotBlank(message = "审核人不能为空")
    private String verifyUserId;

    /**
     * 审核状态。
     */
    @NotNull(message = "审核状态不能为空")
    private Integer verifyStatus;

    /**
     * 驳回原因。
     */
    @Size(max = 255, message = "驳回原因长度不能超过255")
    private String rejectReason;
}
