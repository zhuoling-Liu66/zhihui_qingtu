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

import java.time.LocalDateTime;

/**
 * 推荐日志实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("recommend_log")
@EqualsAndHashCode(callSuper = true)
public class RecommendLog extends BaseEntity {

    /**
     * 日志主键。
     */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

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
     * 推荐时间。
     */
    @TableField("recommend_time")
    private LocalDateTime recommendTime;

    /**
     * 是否点击。
     */
    @TableField("is_click")
    private Integer isClick;

    /**
     * 是否报名。
     */
    @TableField("is_enroll")
    private Integer isEnroll;
}
