package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史消息。
 *
 * <p>{@code cards} 由服务端从推荐日志回填，刷新页面后不必重新调模型就能还原卡片。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    private Long messageId;

    /** user / assistant / tool / system。 */
    private String role;

    private String content;

    private CardsVO cards;

    private PendingActionVO pendingAction;

    private LocalDateTime createTime;
}
