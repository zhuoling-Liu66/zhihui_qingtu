package com.example.zhihuiqingtu.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.zhihuiqingtu.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 推荐曝光与归因日志实体。
 *
 * <p>用途：离线评估推荐效果（CTR、报名转化）、刷新页面时回填卡片、A/B 对比。
 * <p>与 {@link AiToolAuditLog} 的区别：这张表记"推荐了什么"，那张表记"模型调了什么工具"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_recommendation_log")
@EqualsAndHashCode(callSuper = true)
public class AiRecommendationLog extends BaseEntity {

    /** 日志主键。 */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 被推荐用户。 */
    @TableField("user_id")
    private String userId;

    /** 来源会话（首页推荐位为空）。 */
    @TableField("conversation_id")
    private Long conversationId;

    /** 来源消息。 */
    @TableField("message_id")
    private Long messageId;

    /** 目标类型：ACTIVITY / TEAM。 */
    @TableField("target_type")
    private String targetType;

    /** 活动ID。 */
    @TableField("activity_id")
    private Long activityId;

    /** 队伍ID。 */
    @TableField("team_id")
    private Long teamId;

    /** 场景：HOME / CHAT / FILTER / PERSONALIZED。 */
    @TableField("scene")
    private String scene;

    /** 推荐分数。 */
    @TableField("score")
    private BigDecimal score;

    /** 展示位次，从 1 开始。 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 展示给用户的推荐理由。 */
    @TableField("reason")
    private String reason;

    /** 实验分组：RULE / LLM。 */
    @TableField("ab_group")
    private String abGroup;

    /** 是否点击：0-否 1-是。 */
    @TableField("is_clicked")
    private Integer isClicked;

    /** 是否报名：0-否 1-是。 */
    @TableField("is_enrolled")
    private Integer isEnrolled;
}
