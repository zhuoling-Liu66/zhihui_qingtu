package com.example.zhihuiqingtu.dto.team;

import lombok.Data;

/**
 * 队伍查询请求。
 */
@Data
public class TeamQueryRequest {

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 队伍状态。
     */
    private Integer teamStatus;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
