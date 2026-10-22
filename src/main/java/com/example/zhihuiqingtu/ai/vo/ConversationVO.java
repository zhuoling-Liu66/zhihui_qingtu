package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话摘要（列表页用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    private Long conversationId;
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createTime;
}
