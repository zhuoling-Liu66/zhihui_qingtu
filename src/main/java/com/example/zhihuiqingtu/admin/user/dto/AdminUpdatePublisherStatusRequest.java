package com.example.zhihuiqingtu.admin.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改用户发布资格的请求。
 */
@Data
public class AdminUpdatePublisherStatusRequest {

    /**
     * 0 未开通 / 1 已开通。
     */
    @NotNull(message = "发布资格状态不能为空")
    private Integer publisherStatus;
}
