package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 待确认动作。
 *
 * <p><b>它不是"已执行"</b>：只代表模型登记了一个意图，用户确认后才真正写库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingActionVO {

    /** act_ 前缀 + 随机串。 */
    private String actionId;

    /** ENROLL_ACTIVITY / JOIN_TEAM。 */
    private String type;

    /** PENDING / EXECUTED / CANCELLED / EXPIRED / FAILED。 */
    private String status;

    /** 过期时间。 */
    private LocalDateTime expiresAt;

    /** 展示摘要：活动标题、时间、地点、剩余名额。 */
    private Map<String, Object> summary;

    private String confirmUrl;
    private String cancelUrl;
}
