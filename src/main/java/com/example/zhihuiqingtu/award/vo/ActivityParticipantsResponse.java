package com.example.zhihuiqingtu.award.vo;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动参与人员管理返回对象：活动摘要 + 汇总计数 + 分页名单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityParticipantsResponse {

    private Long activityId;

    private String activityTitle;

    /**
     * 活动类型：1 第二课堂学分 / 2 志愿服务 / 3 运动健身 / 4 兴趣团建。
     */
    private Integer activityType;

    /**
     * 活动默认学分（第二课堂活动）。
     */
    private BigDecimal activityCredit;

    /**
     * 学分类型（第二课堂活动）。
     */
    private Integer creditType;

    /**
     * 学分模式：1 固定 / 2 组合。
     */
    private Integer creditMode;

    /**
     * 组合学分档位及占用情况（仅组合模式）。
     */
    private List<CreditTierVO> creditTiers;

    /**
     * 志愿时长（志愿活动）。
     */
    private BigDecimal volunteerHours;

    /**
     * 志愿时长模式：1 固定 / 2 灵活。
     */
    private Integer volunteerMode;

    private Integer reviewStatus;

    private Integer displayStatus;

    private Integer signInEnabled;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 报名总人数。
     */
    private Long totalEnrolled;

    /**
     * 已签到人数（含已签退）。
     */
    private Long signedInCount;

    /**
     * 已签退（已完成）人数。
     */
    private Long completedCount;

    /**
     * 待确认发放记录数。
     */
    private Long pendingAwardCount;

    /**
     * 待管理员审核的发放记录数（灵活志愿时长）。
     */
    private Long reviewingAwardCount;

    /**
     * 已发放记录数。
     */
    private Long approvedAwardCount;

    /**
     * 不发放记录数。
     */
    private Long rejectedAwardCount;

    /**
     * 当前页参与人员。
     */
    private PageResponse<ActivityParticipantVO> page;
}
