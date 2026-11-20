package com.example.zhihuiqingtu.admin.team.dto;

import lombok.Data;

/**
 * 管理端队伍查询条件。
 */
@Data
public class AdminTeamQueryRequest {

    private Long activityId;

    /** 队伍状态：0 招募中 / 1 已满员 / 2 已解散。 */
    private Integer teamStatus;

    /** 队伍名称或队长学号关键词。 */
    private String keyword;

    private Integer pageNo = 0;

    private Integer pageSize = 10;
}
