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

    /**
     * 队长学号或工号。
     */
    @NotBlank(message = "队长不能为空")
    private String leaderUserId;

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
