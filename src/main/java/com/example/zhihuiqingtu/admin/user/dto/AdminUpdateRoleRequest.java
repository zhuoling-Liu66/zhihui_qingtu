package com.example.zhihuiqingtu.admin.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改用户角色的请求。
 */
@Data
public class AdminUpdateRoleRequest {

    /**
     * 1 学生 / 2 管理员 / 3 老师 / 4 运营人员。
     */
    @NotNull(message = "角色不能为空")
    private Integer userRole;
}
