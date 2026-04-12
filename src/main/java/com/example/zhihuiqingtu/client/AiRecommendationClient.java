package com.example.zhihuiqingtu.client;

import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;

/**
 * AI 推荐客户端接口。
 */
public interface AiRecommendationClient {

    RecommendationQueryResponse queryRecommendations(RecommendationQueryRequest request);
}
