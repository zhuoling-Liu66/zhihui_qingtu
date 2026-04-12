package com.example.zhihuiqingtu.dto.enrollment;

import lombok.Data;

/**
 * 报名记录查询请求。
 */
@Data
public class EnrollmentQueryRequest {

    /**
     * 用户学号或工号。
     */
    private String userId;

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 报名状态。
     */
    private Integer enrollStatus;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
