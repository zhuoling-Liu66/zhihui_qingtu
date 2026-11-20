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

import java.time.LocalDateTime;
import com.example.zhihuiqingtu.common.entity.BaseEntity;

/**
 * 队伍成员实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("team_member")
@EqualsAndHashCode(callSuper = true)
public class TeamMember extends BaseEntity {

    /**
     * 成员主键。
     */
    @TableId(value = "member_id", type = IdType.AUTO)
    private Long memberId;

    /**
     * 队伍ID。
     */
    @TableField("team_id")
    private Long teamId;

    /**
     * 用户学号或工号。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 成员状态。
     */
    @TableField("member_status")
    private Integer memberStatus;

    /**
     * 加入时间。
     */
    @TableField("join_time")
    private LocalDateTime joinTime;
}
