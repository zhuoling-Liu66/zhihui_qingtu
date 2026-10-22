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

import java.time.LocalDateTime;

/**
 * 待确认动作实体（写操作的两阶段确认）。
 *
 * <p><b>为什么需要这张表</b>：AI 工具只负责"登记意图"，不直接写业务表。
 * 用户点确认后，才由 {@code /api/ai/actions/{id}/confirm} 调用既有 Service 执行。
 * 这样即使模型被提示词注入，也最多生成一堆待确认卡片，不会产生真实副作用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_pending_action")
@EqualsAndHashCode(callSuper = true)
public class AiPendingAction extends BaseEntity {

    /** 动作ID：act_ 前缀 + 随机串，由应用生成（不是自增）。 */
    @TableId(value = "action_id", type = IdType.INPUT)
    private String actionId;

    /** 发起人学号或工号。 */
    @TableField("user_id")
    private String userId;

    /** 来源会话。 */
    @TableField("conversation_id")
    private Long conversationId;

    /** 来源消息。 */
    @TableField("message_id")
    private Long messageId;

    /** 动作类型：ENROLL_ACTIVITY / JOIN_TEAM。 */
    @TableField("type")
    private String type;

    /** 执行参数（JSON 字符串）。 */
    @TableField("arguments")
    private String arguments;

    /** 展示摘要。 */
    @TableField("summary")
    private String summary;

    /** 状态：PENDING / EXECUTED / CANCELLED / EXPIRED / FAILED。 */
    @TableField("status")
    private String status;

    /** 过期时间。 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 执行时间。 */
    @TableField("executed_at")
    private LocalDateTime executedAt;

    /** 执行结果或失败原因。 */
    @TableField("result_message")
    private String resultMessage;
}
