package com.example.zhihuiqingtu.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.example.zhihuiqingtu.common.entity.BaseEntity;

/**
 * 活动主表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_main")
@EqualsAndHashCode(callSuper = true)
public class ActivityMain extends BaseEntity {

    /**
     * 活动主键，数据库自动生成。
     */
    @TableId(value = "activity_id", type = IdType.AUTO)
    private Long activityId;

    /**
     * 发布者学号或工号。
     */
    @TableField("publisher_user_id")
    private String publisherUserId;

    /**
     * 活动标题。
     */
    @TableField("activity_title")
    private String activityTitle;

    /**
     * 活动描述。
     */
    @TableField("activity_desc")
    private String activityDesc;

    /**
     * 活动类型。
     */
    @TableField("activity_type")
    private Integer activityType;

    /**
     * 活动学分，仅第二课堂学分活动使用。
     */
    @TableField("activity_credit")
    private BigDecimal activityCredit;

    /**
     * 学分类型，仅第二课堂学分活动使用。
     */
    @TableField("credit_type")
    private Integer creditType;

    /**
     * 学分模式：1 固定学分 / 2 组合学分（按档位与名额分配）；非第二课堂活动为空。
     */
    @TableField("credit_mode")
    private Integer creditMode;

    /**
     * 志愿服务时长，仅志愿服务活动使用。
     */
    @TableField("volunteer_hours")
    private BigDecimal volunteerHours;

    /**
     * 志愿时长模式：1 固定时长 / 2 灵活时长（活动结束后逐人录入）；非志愿活动为空。
     */
    @TableField("volunteer_mode")
    private Integer volunteerMode;

    /**
     * 活动封面地址。
     */
    @TableField("cover_url")
    private String coverUrl;

    /**
     * 开始时间。
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 活动地点。
     */
    @TableField("activity_location")
    private String activityLocation;

    /**
     * 最大参与人数。
     */
    @TableField("max_participant")
    private Integer maxParticipant;

    /**
     * 当前参与人数。
     */
    @TableField("current_participant")
    private Integer currentParticipant;

    /**
     * 活动状态，仅持久化“已发布/已取消”。
     */
    @TableField("activity_status")
    private Integer activityStatus;

    /**
     * 是否开放签到，0-关闭 1-开启。
     */
    @TableField("sign_in_enabled")
    private Integer signInEnabled;

    /**
     * 活动审核状态：0 待审核 / 1 已通过 / 2 已驳回。
     *
     * <p>新发布的活动一律先进入待审核；只有审核通过的活动才会出现在公开列表，
     * 也才允许报名。活动内容被修改后重新回到待审核。
     */
    @TableField("review_status")
    private Integer reviewStatus;

    /**
     * 审核驳回原因（或审核备注）。
     */
    @TableField("review_reason")
    private String reviewReason;

    /**
     * 审核人学号或工号。
     */
    @TableField("reviewer_user_id")
    private String reviewerUserId;

    /**
     * 审核时间。
     */
    @TableField("review_time")
    private LocalDateTime reviewTime;
}
