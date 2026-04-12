package com.example.zhihuiqingtu.client;

import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * AI 推荐 HTTP 客户端。
 */
@Component
@RequiredArgsConstructor
public class HttpAiRecommendationClient implements AiRecommendationClient {

    private final RestTemplate restTemplate;

    @Value("${ai.recommendation.base-url}")
    private String baseUrl;

    @Override
    public RecommendationQueryResponse queryRecommendations(RecommendationQueryRequest request) {
        try {
            return restTemplate.postForObject(
                    baseUrl + "/recommendations/query",
                    request,
                    RecommendationQueryResponse.class
            );
        } catch (RestClientException exception) {
            return RecommendationQueryResponse.builder()
                    .recommendedActivityIds(Collections.emptyList())
                    .scores(Collections.emptyMap())
                    .reasonTags(Collections.emptyMap())
                    .build();
        }
    }
}
