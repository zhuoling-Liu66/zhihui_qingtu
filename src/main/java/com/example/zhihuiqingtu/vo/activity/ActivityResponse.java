package com.example.zhihuiqingtu.vo.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 发布者学号或工号。
     */
    private String publisherUserId;

    /**
     * 活动标题。
     */
    private String activityTitle;

    /**
     * 活动描述。
     */
    private String activityDesc;

    /**
     * 活动类型。
     */
    private Integer activityType;

    /**
     * 活动学分。
     */
    private BigDecimal activityCredit;

    /**
     * 学分类型。
     */
    private Integer creditType;

    /**
     * 志愿服务时长。
     */
    private BigDecimal volunteerHours;

    /**
     * 活动封面地址。
     */
    private String coverUrl;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 活动地点。
     */
    private String activityLocation;

    /**
     * 最大参与人数。
     */
    private Integer maxParticipant;

    /**
     * 当前参与人数。
     */
    private Integer currentParticipant;

    /**
     * 持久化状态。
     */
    private Integer activityStatus;

    /**
     * 展示状态。
     */
    private Integer displayStatus;

    /**
     * 是否开放签到，0-关闭 1-开启。
     */
    private Integer signInEnabled;

    /**
     * 标签信息。
     */
    private List<ActivityTagVO> tags;
}
