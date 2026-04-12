package com.example.zhihuiqingtu.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审核成员请求。
 */
@Data
public class ReviewTeamMemberRequest {

    /**
     * 队长学号或工号。
     */
    @NotBlank(message = "队长不能为空")
    private String leaderUserId;

    /**
     * 审核后的成员状态。
     */
    @NotNull(message = "成员状态不能为空")
    private Integer memberStatus;
}
