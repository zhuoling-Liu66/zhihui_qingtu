package com.example.zhihuiqingtu.dto.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 申请加入队伍请求。
 */
@Data
public class JoinTeamRequest {

    /**
     * 用户学号或工号。
     */
    @NotBlank(message = "用户不能为空")
    private String userId;
}
