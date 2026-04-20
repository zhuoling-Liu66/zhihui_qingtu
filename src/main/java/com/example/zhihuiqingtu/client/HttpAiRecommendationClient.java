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
 * 基于 HTTP 的 AI 推荐客户端实现。
 * <p>
 * 当前项目里，Java 主后端通过 HTTP 调用 Python 推荐服务。
 * 这个类就是具体的 HTTP 调用实现。
 */
@Component
@RequiredArgsConstructor
public class HttpAiRecommendationClient implements AiRecommendationClient {

    /**
     * Spring 提供的 HTTP 请求工具。
     */
    private final RestTemplate restTemplate;

    /**
     * Python 推荐服务的基础地址，从配置文件中读取。
     */
    @Value("${ai.recommendation.base-url}")
    private String baseUrl;

    /**
     * 调用 Python 推荐接口。
     * <p>
     * 如果远程服务异常，为了不影响主系统整体可用性，
     * 这里会返回一个“空推荐结果”而不是直接让系统报错。
     *
     * @param request 推荐请求参数
     * @return 推荐结果；若远程服务失败，则返回空结果
     */
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
