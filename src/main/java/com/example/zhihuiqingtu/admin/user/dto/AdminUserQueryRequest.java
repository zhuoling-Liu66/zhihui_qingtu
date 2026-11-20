package com.example.zhihuiqingtu.admin.user.dto;

import lombok.Data;

/**
 * 管理端用户查询条件。
 */
@Data
public class AdminUserQueryRequest {

    /** 学号、姓名、手机号或邮箱关键词。 */
    private String keyword;

    /** 角色编码：1 学生 / 2 管理员 / 3 老师 / 4 运营人员。 */
    private Integer userRole;

    /** 发布权限状态：0 未开通 / 1 已开通。 */
    private Integer publisherStatus;

    private Integer pageNo = 0;

    private Integer pageSize = 10;
}
