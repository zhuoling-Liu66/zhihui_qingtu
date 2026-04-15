package com.example.zhihuiqingtu.dto.credit;

import lombok.Data;

/**
 * 学分记录查询请求。
 */
@Data
public class CreditQueryRequest {

    /**
     * 用户学号或工号。
     */
    private String userId;

    /**
     * 审核状态。
     */
    private Integer verifyStatus;

    /**
     * 学分类型。
     */
    private Integer creditType;

    private Long activityId;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
