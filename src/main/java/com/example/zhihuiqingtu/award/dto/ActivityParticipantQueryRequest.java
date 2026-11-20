package com.example.zhihuiqingtu.award.dto;

import lombok.Data;

/**
 * 活动参与人员查询条件。
 */
@Data
public class ActivityParticipantQueryRequest {

    /**
     * 报名状态筛选：1 已报名 / 3 已签到 / 4 已完成；为空不筛选。
     */
    private Integer enrollStatus;

    private Integer pageNo = 0;

    private Integer pageSize = 10;
}
