package com.example.zhihuiqingtu.enrollment.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报名记录返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentResponse {

    /**
     * 报名ID。
     */
    private Long enrollId;

    /**
     * 用户学号或工号。
     */
    private String userId;

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 活动标题。
     */
    private String activityTitle;

    /**
     * 活动封面。
     */
    private String activityCoverUrl;

    /**
     * 活动地点。
     */
    private String activityLocation;

    /**
     * 活动开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 活动结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 活动持久化状态。
     */
    private Integer activityStatus;

    /**
     * 活动展示状态。
     */
    private Integer displayStatus;

    /**
     * 活动是否开放签到。
     */
    private Integer signInEnabled;

    /**
     * 报名状态。
     */
    private Integer enrollStatus;

    /**
     * 签到时间。
     */
    private LocalDateTime signInTime;

    /**
     * 签退时间；签退后报名状态为已完成。
     */
    private LocalDateTime signOutTime;

    /**
     * 评分。
     */
    private Integer feedbackScore;

    /**
     * 评价内容。
     */
    private String feedbackContent;

    /**
     * 是否可签到。
     */
    private Boolean canSignIn;

    /**
     * 是否可签退（已签到且未签退、活动未取消）。
     */
    private Boolean canSignOut;

    /**
     * 是否可退出。
     */
    private Boolean canCancel;

    private Boolean canFeedback;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
