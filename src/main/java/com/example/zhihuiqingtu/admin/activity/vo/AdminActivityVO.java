package com.example.zhihuiqingtu.admin.activity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端活动列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminActivityVO {

    private Long activityId;

    private String activityTitle;

    private String publisherUserId;

    private String publisherName;

    private Integer activityType;

    private java.math.BigDecimal activityCredit;

    private Integer creditType;

    /**
     * 学分模式：1 固定 / 2 组合。
     */
    private Integer creditMode;

    /**
     * 志愿时长模式：1 固定 / 2 灵活。
     */
    private Integer volunteerMode;

    private java.math.BigDecimal volunteerHours;

    private String activityDesc;

    private String coverUrl;

    private Integer activityStatus;

    /** 按结束时间现算的展示状态（1 未结束 / 3 已结束 / 4 已取消）。 */
    private Integer displayStatus;

    /** 审核状态：0 待审核 / 1 已通过 / 2 已驳回。 */
    private Integer reviewStatus;

    private String reviewReason;

    private String reviewerUserId;

    private LocalDateTime reviewTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String activityLocation;

    private Integer maxParticipant;

    private Integer currentParticipant;

    private Integer signInEnabled;

    private LocalDateTime createTime;
}
