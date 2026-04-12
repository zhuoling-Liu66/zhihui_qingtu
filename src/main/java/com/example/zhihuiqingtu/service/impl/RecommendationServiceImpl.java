package com.example.zhihuiqingtu.service.impl;

import com.example.zhihuiqingtu.client.AiRecommendationClient;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationLogRequest;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.entity.RecommendLog;
import com.example.zhihuiqingtu.repository.RecommendLogRepository;
import com.example.zhihuiqingtu.service.RecommendationService;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 推荐服务实现。
 */
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final AiRecommendationClient aiRecommendationClient;
    private final RecommendLogRepository recommendLogRepository;

    @Override
    public RecommendationQueryResponse queryRecommendations(RecommendationQueryRequest request) {
        return aiRecommendationClient.queryRecommendations(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logRecommendation(RecommendationLogRequest request) {
        RecommendLog recommendLog = RecommendLog.builder()
                .userId(request.getUserId())
                .activityId(request.getActivityId())
                .recommendTime(LocalDateTime.now())
                .isClick(request.getIsClick())
                .isEnroll(request.getIsEnroll())
                .build();
        recommendLogRepository.insert(recommendLog);
    }
}
