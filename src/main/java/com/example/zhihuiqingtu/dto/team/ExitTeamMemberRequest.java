package com.example.zhihuiqingtu.dto.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 退出队伍请求。
 */
@Data
public class ExitTeamMemberRequest {

    /**
     * 操作人学号或工号。
     */
    @NotBlank(message = "操作人不能为空")
    private String operatorUserId;
}
