package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.client.AiRecommendationClient;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationLogRequest;
import com.example.zhihuiqingtu.dto.recommendation.RecommendationQueryRequest;
import com.example.zhihuiqingtu.entity.RecommendLog;
import com.example.zhihuiqingtu.repository.RecommendLogRepository;
import com.example.zhihuiqingtu.service.impl.RecommendationServiceImpl;
import com.example.zhihuiqingtu.vo.recommendation.RecommendationQueryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock
    private AiRecommendationClient aiRecommendationClient;

    @Mock
    private RecommendLogRepository recommendLogRepository;

    @InjectMocks
    private RecommendationServiceImpl recommendationService;

    @Test
    void shouldDelegateRecommendationQueryToClient() {
        RecommendationQueryRequest request = new RecommendationQueryRequest();
        request.setUserId("20240001");
        RecommendationQueryResponse response = RecommendationQueryResponse.builder()
                .recommendedActivityIds(List.of(10L, 11L))
                .scores(Map.of(10L, 0.95D))
                .reasonTags(Map.of(10L, List.of("兴趣匹配")))
                .build();
        when(aiRecommendationClient.queryRecommendations(any(RecommendationQueryRequest.class))).thenReturn(response);

        RecommendationQueryResponse result = recommendationService.queryRecommendations(request);

        assertEquals(2, result.getRecommendedActivityIds().size());
        verify(aiRecommendationClient).queryRecommendations(request);
    }

    @Test
    void shouldPersistRecommendationLog() {
        RecommendationLogRequest request = new RecommendationLogRequest();
        request.setUserId("20240001");
        request.setActivityId(2L);
        request.setIsClick(1);
        request.setIsEnroll(0);

        recommendationService.logRecommendation(request);

        ArgumentCaptor<RecommendLog> captor = ArgumentCaptor.forClass(RecommendLog.class);
        verify(recommendLogRepository).insert(captor.capture());
        RecommendLog savedLog = captor.getValue();
        assertEquals("20240001", savedLog.getUserId());
        assertEquals(2L, savedLog.getActivityId());
        assertEquals(1, savedLog.getIsClick());
    }
}
