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
 * AI 推荐预留接口控制器。
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 查询推荐结果。
     */
    @PostMapping("/query")
    public ApiResponse<RecommendationQueryResponse> queryRecommendations(
            @Valid @RequestBody RecommendationQueryRequest request) {
        return ApiResponse.success(recommendationService.queryRecommendations(request));
    }

    /**
     * 记录推荐日志。
     */
    @PostMapping("/logs")
    public ApiResponse<Void> logRecommendation(@Valid @RequestBody RecommendationLogRequest request) {
        recommendationService.logRecommendation(request);
        return ApiResponse.success("推荐日志记录成功", null);
    }
}
