package com.example.zhihuiqingtu.award.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动参与人员（发布者/管理员视图）。
 *
 * <p>报名信息与发放记录合并展示：第二课堂活动看学分，志愿活动看时长，
 * 其他活动只有考勤信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityParticipantVO {

    /**
     * 报名记录ID。
     */
    private Long enrollId;

    /**
     * 用户学号或工号。
     */
    private String userId;

    /**
     * 用户姓名。
     */
    private String userName;

    /**
     * 报名状态：1 已报名 / 3 已签到 / 4 已完成。
     */
    private Integer enrollStatus;

    /**
     * 签到时间。
     */
    private LocalDateTime signInTime;

    /**
     * 签退时间。
     */
    private LocalDateTime signOutTime;

    /**
     * 活动评分。
     */
    private Integer feedbackScore;

    /**
     * 活动评价。
     */
    private String feedbackContent;

    /**
     * 报名时间。
     */
    private LocalDateTime createTime;

    /**
     * 发放记录ID；未签退时为空。
     */
    private Long recordId;

    /**
     * 发放学分（第二课堂活动）。
     */
    private BigDecimal credit;

    /**
     * 学分类型（第二课堂活动）。
     */
    private Integer creditType;

    /**
     * 发放志愿时长（志愿活动）。
     */
    private BigDecimal volunteerHours;

    /**
     * 发放状态：0 待确认 / 1 已发放 / 2 不发放。
     */
    private Integer verifyStatus;

    /**
     * 不发放原因。
     */
    private String rejectReason;

    /**
     * 确认/调整时间。
     */
    private LocalDateTime verifyTime;
}
