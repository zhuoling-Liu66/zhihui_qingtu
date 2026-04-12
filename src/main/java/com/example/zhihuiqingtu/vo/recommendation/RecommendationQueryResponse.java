package com.example.zhihuiqingtu.vo.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 推荐结果返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationQueryResponse {

    /**
     * 推荐活动ID列表。
     */
    private List<Long> recommendedActivityIds;

    /**
     * 推荐分数字典。
     */
    private Map<Long, Double> scores;

    /**
     * 推荐理由标签。
     */
    private Map<Long, List<String>> reasonTags;
}
