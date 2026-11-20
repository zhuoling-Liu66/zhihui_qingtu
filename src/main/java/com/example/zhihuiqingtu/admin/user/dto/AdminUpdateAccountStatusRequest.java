package com.example.zhihuiqingtu.admin.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改账号状态的请求。
 */
@Data
public class AdminUpdateAccountStatusRequest {

    /**
     * 0 正常 / 1 禁用。
     */
    @NotNull(message = "账号状态不能为空")
    private Integer accountStatus;
}
