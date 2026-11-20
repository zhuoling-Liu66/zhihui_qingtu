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
import com.example.zhihuiqingtu.common.entity.BaseEntity;

/**
 * 组合学分档位：一个活动下不同学分对应不同名额。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_credit_tier")
@EqualsAndHashCode(callSuper = true)
public class ActivityCreditTier extends BaseEntity {

    /**
     * 档位主键。
     */
    @TableId(value = "tier_id", type = IdType.AUTO)
    private Long tierId;

    /**
     * 活动ID。
     */
    @TableField("activity_id")
    private Long activityId;

    /**
     * 该档学分。
     */
    @TableField("credit")
    private BigDecimal credit;

    /**
     * 该档名额。
     */
    @TableField("quota")
    private Integer quota;

    /**
     * 排序号。
     */
    @TableField("sort_no")
    private Integer sortNo;
}
