package com.example.zhihuiqingtu.dto.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 解散队伍请求。
 */
@Data
public class DismissTeamRequest {

    /**
     * 操作人学号或工号。
     */
    @NotBlank(message = "操作人不能为空")
    private String operatorUserId;
}
