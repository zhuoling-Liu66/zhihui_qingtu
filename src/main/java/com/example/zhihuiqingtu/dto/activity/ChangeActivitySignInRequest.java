package com.example.zhihuiqingtu.dto.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新活动签到开关请求。
 */
@Data
public class ChangeActivitySignInRequest {

    /**
     * 操作人学号或工号。
     */
    @NotBlank(message = "操作人不能为空")
    private String operatorUserId;

    /**
     * 是否开启签到，0-关闭 1-开启。
     */
    @NotNull(message = "签到开关不能为空")
    private Integer signInEnabled;
}
