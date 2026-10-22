package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐位响应（活动/队伍共用）。
 *
 * @param <T> 卡片类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationVO<T> {

    private List<T> items;

    /** true 表示没走 LLM 精排（规则结果或降级）。 */
    private Boolean degraded;

    private LocalDateTime generatedAt;
}
