package com.example.zhihuiqingtu.team.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 队伍返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {

    /**
     * 队伍ID。
     */
    private Long teamId;

    /**
     * 关联活动ID。
     */
    private Long activityId;

    private String activityTitle;

    /**
     * 队长学号或工号。
     */
    private String leaderUserId;

    /**
     * 队伍名称。
     */
    private String teamName;

    /**
     * 队伍简介。
     */
    private String teamDesc;

    /**
     * 最大人数。
     */
    private Integer maxSize;

    /**
     * 当前人数。
     */
    private Integer currentSize;

    /**
     * 队伍状态。
     */
    private Integer teamStatus;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    private List<TeamMemberVO> members;
}
