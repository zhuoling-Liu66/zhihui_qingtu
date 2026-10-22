package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 队伍卡片。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCardVO {

    private Long teamId;
    private String teamName;
    private Long activityId;
    private String activityTitle;
    private String leaderUserId;
    private String leaderName;
    private Integer maxSize;
    private Integer currentSize;
    /** 缺几人 = maxSize - currentSize。 */
    private Integer missing;
    /** 0 招募中 / 1 已满员 / 2 已解散。 */
    private Integer teamStatus;
    private String teamStatusName;
    private String reason;
}
