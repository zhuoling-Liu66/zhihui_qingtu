package com.example.zhihuiqingtu.admin.enrollment.dto;

import lombok.Data;

/**
 * 管理端报名查询条件。
 */
@Data
public class AdminEnrollmentQueryRequest {

    private Long activityId;

    /** 学号模糊匹配。 */
    private String userId;

    /** 报名状态：1 已报名 / 3 已签到 / 4 已完成。 */
    private Integer enrollStatus;

    /** 是否已签到：0 未签到 / 1 已签到；为空不筛选。 */
    private Integer signedIn;

    private Integer pageNo = 0;

    private Integer pageSize = 10;
}
