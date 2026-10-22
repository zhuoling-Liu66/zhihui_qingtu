package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 额度信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaVO {

    private Long dailyTokenLimit;
    private Long usedTokens;
    private Long remainingTokens;
    private LocalDateTime resetAt;
    private Integer requestsPerMinute;

    /** false 表示管理员关闭了 AI 功能，前端应隐藏入口。 */
    private Boolean enabled;
}
