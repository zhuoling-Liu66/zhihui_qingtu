package com.example.zhihuiqingtu.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 推荐反馈埋点请求。
 */
@Data
public class FeedbackRequest {

    /** 来源会话（助手消息里的卡片才需要）。 */
    private Long conversationId;

    /** 来源消息。 */
    private Long messageId;

    /** 目标类型：ACTIVITY / TEAM。 */
    @NotBlank(message = "targetType 不能为空")
    private String targetType;

    /** 活动ID 或 队伍ID。 */
    @NotNull(message = "targetId 不能为空")
    private Long targetId;

    /** 事件：IMPRESSION / CLICK / ENROLL。 */
    @NotBlank(message = "event 不能为空")
    private String event;

    /** 场景：HOME / CHAT / FILTER / PERSONALIZED。 */
    private String scene;
}
