package com.example.zhihuiqingtu.admin.team.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端队伍列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTeamVO {

    private Long teamId;

    private String teamName;

    private String teamDesc;

    private Long activityId;

    private String activityTitle;

    private String leaderUserId;

    private String leaderName;

    private Integer currentSize;

    private Integer maxSize;

    private Integer teamStatus;

    private LocalDateTime createTime;
}
