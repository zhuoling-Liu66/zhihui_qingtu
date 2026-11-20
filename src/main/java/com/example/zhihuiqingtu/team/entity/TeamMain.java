package com.example.zhihuiqingtu.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.example.zhihuiqingtu.common.entity.BaseEntity;

/**
 * 队伍主表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("team_main")
@EqualsAndHashCode(callSuper = true)
public class TeamMain extends BaseEntity {

    /**
     * 队伍主键。
     */
    @TableId(value = "team_id", type = IdType.AUTO)
    private Long teamId;

    /**
     * 关联活动ID。
     */
    @TableField("activity_id")
    private Long activityId;

    /**
     * 队长学号或工号。
     */
    @TableField("leader_user_id")
    private String leaderUserId;

    /**
     * 队伍名称。
     */
    @TableField("team_name")
    private String teamName;

    /**
     * 队伍简介。
     */
    @TableField("team_desc")
    private String teamDesc;

    /**
     * 最大人数。
     */
    @TableField("max_size")
    private Integer maxSize;

    /**
     * 当前人数。
     */
    @TableField("current_size")
    private Integer currentSize;

    /**
     * 队伍状态。
     */
    @TableField("team_status")
    private Integer teamStatus;
}
