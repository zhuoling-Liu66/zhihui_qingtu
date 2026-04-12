package com.example.zhihuiqingtu.dto.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新活动状态请求。
 */
@Data
public class ChangeActivityStatusRequest {

    /**
     * 操作人学号或工号。
     */
    @NotBlank(message = "操作人不能为空")
    private String operatorUserId;

    /**
     * 目标状态，仅支持已发布/已取消。
     */
    @NotNull(message = "活动状态不能为空")
    private Integer activityStatus;
}
