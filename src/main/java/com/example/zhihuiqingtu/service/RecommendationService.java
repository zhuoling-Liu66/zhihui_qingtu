package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.recommendation.RecommendationLogRequest;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;

/**
 * 推荐服务接口。
 */
public interface RecommendationService {

    RecommendationQueryResponse queryRecommendations(RecommendationQueryRequest request);

    void logRecommendation(RecommendationLogRequest request);
}
