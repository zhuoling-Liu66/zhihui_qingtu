package com.example.zhihuiqingtu.client;

import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;

/**
 * AI 推荐客户端接口。
 * <p>
 * 这个接口专门负责抽象“如何向 Python 推荐服务发请求”。
 * 业务层依赖接口而不是具体实现，可以降低耦合度。
 */
public interface AiRecommendationClient {

    /**
     * 请求 AI 推荐服务，获取推荐活动结果。
     *
     * @param request 推荐请求参数
     * @return 推荐结果
     */
    RecommendationQueryResponse queryRecommendations(RecommendationQueryRequest request);
}
