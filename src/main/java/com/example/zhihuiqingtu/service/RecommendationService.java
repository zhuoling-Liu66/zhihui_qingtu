package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.dto.recommendation.RecommendationLogRequest;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;

/**
 * 推荐业务服务接口。
 */
public interface RecommendationService {

    /**
     * 查询推荐结果。
     *
     * @param request 推荐请求参数
     * @return 推荐结果
     */
    RecommendationQueryResponse queryRecommendations(RecommendationQueryRequest request);

    /**
     * 写入推荐日志。
     *
     * @param request 推荐日志请求参数
     */
    void logRecommendation(RecommendationLogRequest request);
}
