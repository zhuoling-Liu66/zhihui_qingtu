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
 * AI 会话实体。
 *
 * <p>一个用户可以有多个会话；会话是"消息"和"待确认动作"的归属边界，
 * 也是越权校验的第一道关：查会话必须同时带 {@code conversation_id + user_id}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_conversation")
@EqualsAndHashCode(callSuper = true)
public class AiConversation extends BaseEntity {

    /** 会话主键。 */
    @TableId(value = "conversation_id", type = IdType.AUTO)
    private Long conversationId;

    /** 所属用户学号或工号。 */
    @TableField("user_id")
    private String userId;

    /** 会话标题。 */
    @TableField("title")
    private String title;

    /** 场景：chat-通用对话 recommend-推荐。 */
    @TableField("scene")
    private String scene;

    /** 较早消息的压缩摘要。 */
    @TableField("summary")
    private String summary;

    /** 消息条数（不含 tool 角色）。 */
    @TableField("message_count")
    private Integer messageCount;

    /** 最后一条消息时间。 */
    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    /** 会话状态：1-正常 0-关闭。 */
    @TableField("status")
    private Integer status;
}
