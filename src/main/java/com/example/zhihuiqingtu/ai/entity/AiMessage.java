package com.example.zhihuiqingtu.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.zhihuiqingtu.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 会话消息实体。
 *
 * <p>{@code user_id} 是冗余字段（会话表里已有）：按用户审计/导出时不用 join，
 * 也避免"会话删了、消息变孤儿"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_message")
@EqualsAndHashCode(callSuper = true)
public class AiMessage extends BaseEntity {

    /** 消息主键。 */
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;

    /** 所属会话。 */
    @TableField("conversation_id")
    private Long conversationId;

    /** 所属用户（冗余）。 */
    @TableField("user_id")
    private String userId;

    /** 角色：user / assistant / tool / system。 */
    @TableField("role")
    private String role;

    /** 消息文本。 */
    @TableField("content")
    private String content;

    /** role=tool 时的工具名。 */
    @TableField("tool_name")
    private String toolName;

    /** 与模型返回的 tool_call.id 对应。 */
    @TableField("tool_call_id")
    private String toolCallId;

    /** assistant 消息附带的工具调用片段（JSON 字符串）。 */
    @TableField("tool_calls")
    private String toolCalls;

    /** 结构化卡片（JSON 字符串）：刷新页面时直接回填，不必重新调模型。 */
    @TableField("cards")
    private String cards;

    /** 生成该消息的模型名。 */
    @TableField("model")
    private String model;

    /** 输入 token 数。 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 输出 token 数。 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 本次生成耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;
}
