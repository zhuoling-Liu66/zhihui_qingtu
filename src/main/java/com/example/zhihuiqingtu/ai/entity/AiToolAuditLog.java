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

/**
 * 工具调用审计实体。
 *
 * <p>刻意不加外键：会话/用户被删除后审计仍需留存。
 * <p>排障时最常看这张表：用户说"报名失败"→ 查 {@code tool_name=propose_enroll} 的参数与状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_tool_audit_log")
@EqualsAndHashCode(callSuper = true)
public class AiToolAuditLog extends BaseEntity {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 调用用户。 */
    @TableField("user_id")
    private String userId;

    /** 来源会话。 */
    @TableField("conversation_id")
    private Long conversationId;

    /** 工具名。 */
    @TableField("tool_name")
    private String toolName;

    /** 入参（已脱敏，不含身份字段）。 */
    @TableField("arguments")
    private String arguments;

    /** 状态：SUCCESS / FAILED / DENIED / TIMEOUT。 */
    @TableField("status")
    private String status;

    /** 失败时的业务码。 */
    @TableField("error_code")
    private Integer errorCode;

    /** 结果摘要（截断，不含 PII）。 */
    @TableField("result_summary")
    private String resultSummary;

    /** 耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 链路追踪ID。 */
    @TableField("trace_id")
    private String traceId;
}
