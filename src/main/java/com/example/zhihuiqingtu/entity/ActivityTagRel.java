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

/**
 * 活动标签关联实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_tag_rel")
@EqualsAndHashCode(callSuper = true)
public class ActivityTagRel extends BaseEntity {

    /**
     * 关联主键。
     */
    @TableId(value = "rel_id", type = IdType.AUTO)
    private Long relId;

    /**
     * 活动ID。
     */
    @TableField("activity_id")
    private Long activityId;

    /**
     * 标签ID。
     */
    @TableField("tag_id")
    private Long tagId;
}
