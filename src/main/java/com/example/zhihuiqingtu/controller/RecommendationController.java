package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationLogRequest;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.service.RecommendationService;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 推荐模块控制器。
 * <p>
 * 前端不会直接调用 Python 推荐服务，而是先调用本控制器，
 * 由 Java 后端统一转发并封装结果。
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    /**
     * 推荐业务服务。
     */
    private final RecommendationService recommendationService;

    /**
     * 查询推荐结果。
     *
     * @param request 推荐请求参数
     * @return 推荐结果
     */
    @PostMapping("/query")
    public ApiResponse<RecommendationQueryResponse> queryRecommendations(
            @Valid @RequestBody RecommendationQueryRequest request) {
        return ApiResponse.success(recommendationService.queryRecommendations(request));
    }

    /**
     * 记录推荐日志。
     *
     * @param request 推荐日志请求体
     * @return 空响应体
     */
    @PostMapping("/logs")
    public ApiResponse<Void> logRecommendation(@Valid @RequestBody RecommendationLogRequest request) {
        recommendationService.logRecommendation(request);
        return ApiResponse.success("推荐日志记录成功", null);
    }
}
