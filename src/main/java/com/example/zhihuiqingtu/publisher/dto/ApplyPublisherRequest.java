package com.example.zhihuiqingtu.publisher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生申请开通活动发布权限。
 */
@Data
public class ApplyPublisherRequest {

    /**
     * 申请理由，便于管理员审批时了解用途并形成留痕。
     */
    @NotBlank(message = "请填写申请理由")
    @Size(max = 500, message = "申请理由不能超过500字")
    private String applyReason;
}
