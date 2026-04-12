package com.example.zhihuiqingtu.entity;

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
     * 志愿服务时长，仅志愿服务活动使用。
     */
    @TableField("volunteer_hours")
    private BigDecimal volunteerHours;

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
}
