package com.example.zhihuiqingtu.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户活动历史返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityHistoryVO {

    /**
     * 报名记录ID。
     */
    private Long enrollId;

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 活动标题。
     */
    private String activityTitle;

    /**
     * 活动类型。
     */
    private Integer activityType;

    /**
     * 报名状态。
     */
    private Integer enrollStatus;

    /**
     * 活动开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 活动结束时间。
     */
    private LocalDateTime endTime;
}
