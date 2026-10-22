package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 非流式对话响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseVO {

    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;

    /** 助手回复（Markdown）。 */
    private String reply;

    private CardsVO cards;

    /** 有待确认动作时非空。 */
    private PendingActionVO pendingAction;

    private UsageVO usage;

    /** true 表示走了规则降级（没调模型或模型失败）。 */
    private Boolean degraded;
}
