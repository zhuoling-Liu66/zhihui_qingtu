package com.example.zhihuiqingtu.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建队伍请求。
 */
@Data
public class CreateTeamRequest {

    /**
     * 关联活动ID。
     */
    private Long activityId;

    // 【安全迁移】原来这里有一个 leaderUserId 字段（@NotBlank），由客户端在请求体里自报"我是队长"。
    // 那是可伪造的身份声明：任何人都能填别人的学号去建队，把队伍挂到别人名下。
    // 队长的唯一可信来源是 Spring Security 认证后的 JWT（sub claim），
    // 因此该字段已删除，队长身份在 TeamServiceImpl.createTeam 内部通过 CurrentUser 获取。

    /**
     * 队伍名称。
     */
    @NotBlank(message = "队伍名称不能为空")
    @Size(max = 100, message = "队伍名称长度不能超过100")
    private String teamName;

    /**
     * 队伍简介。
     */
    @Size(max = 500, message = "队伍简介长度不能超过500")
    private String teamDesc;

    /**
     * 最大人数。
     */
    @NotNull(message = "最大人数不能为空")
    private Integer maxSize;
}
