package com.example.zhihuiqingtu.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.example.zhihuiqingtu.common.entity.BaseEntity;

/**
 * 活动报名实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_enroll")
@EqualsAndHashCode(callSuper = true)
public class ActivityEnroll extends BaseEntity {

    /**
     * 报名主键。
     */
    @TableId(value = "enroll_id", type = IdType.AUTO)
    private Long enrollId;

    /**
     * 用户学号或工号。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 活动ID。
     */
    @TableField("activity_id")
    private Long activityId;

    /**
     * 报名状态。
     */
    @TableField("enroll_status")
    private Integer enrollStatus;

    /**
     * 签到时间。
     */
    @TableField("sign_in_time")
    private LocalDateTime signInTime;

    /**
     * 签退时间；签退后报名状态流转为“已完成”（4）。
     */
    @TableField("sign_out_time")
    private LocalDateTime signOutTime;

    /**
     * 活动评分。
     */
    @TableField("feedback_score")
    private Integer feedbackScore;

    /**
     * 活动评价。
     */
    @TableField("feedback_content")
    private String feedbackContent;
}
