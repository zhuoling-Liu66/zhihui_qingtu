package com.example.zhihuiqingtu.award.entity;

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
 * 学分记录实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("credit_record")
@EqualsAndHashCode(callSuper = true)
public class CreditRecord extends BaseEntity {

    /**
     * 学分记录主键。
     */
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

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
     * 获得学分。
     */
    @TableField("credit")
    private BigDecimal credit;

    /**
     * 学分类型；志愿时长发放记录为空。
     */
    @TableField("credit_type")
    private Integer creditType;

    /**
     * 发放的志愿时长，仅志愿服务活动使用；学分记录为空。
     */
    @TableField("volunteer_hours")
    private BigDecimal volunteerHours;

    /**
     * 审核人学号或工号。
     */
    @TableField("verify_user_id")
    private String verifyUserId;

    /**
     * 审核状态。
     */
    @TableField("verify_status")
    private Integer verifyStatus;

    /**
     * 驳回原因。
     */
    @TableField("reject_reason")
    private String rejectReason;

    /**
     * 审核时间。
     */
    @TableField("verify_time")
    private LocalDateTime verifyTime;
}
