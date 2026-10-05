package com.example.zhihuiqingtu.dto.team;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审核成员请求。
 */
@Data
public class ReviewTeamMemberRequest {

    // 【安全迁移】原来这里有一个 leaderUserId 字段（@NotBlank），由客户端自报"我是队长"。
    // 那是可伪造的权限声明：任何人填上队长的学号就能审批队员申请。
    // 审核人身份只能来自 Spring Security 认证后的 JWT，
    // 因此该字段已删除，"是否队长"的校验在 TeamServiceImpl.reviewMember 里用当前登录用户比较。

    /**
     * 审核后的成员状态。
     */
    @NotNull(message = "成员状态不能为空")
    private Integer memberStatus;
}
