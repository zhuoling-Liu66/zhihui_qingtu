package com.example.zhihuiqingtu.ai.controller;

import com.example.zhihuiqingtu.ai.dto.FeedbackRequest;
import com.example.zhihuiqingtu.ai.service.AiQuotaService;
import com.example.zhihuiqingtu.ai.service.AiRecommendationService;
import com.example.zhihuiqingtu.ai.vo.ActivityCardVO;
import com.example.zhihuiqingtu.ai.vo.QuotaVO;
import com.example.zhihuiqingtu.ai.vo.RecommendationVO;
import com.example.zhihuiqingtu.ai.vo.TeamCardVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 推荐位、反馈埋点与额度查询。
 *
 * <p><b>推荐位不依赖模型</b>：这是可用性设计的关键——聊天挂了，首页推荐还在。
 */
@Tag(name = "AI 助手-推荐", description = "活动/队伍推荐位、反馈埋点、额度")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final AiRecommendationService recommendationService;
    private final AiQuotaService quotaService;
    private final CurrentUser currentUser;

    @Operation(summary = "活动推荐位", description = "规则推荐，不调用大模型；已报名/已结束/已满员不会出现")
    @GetMapping("/recommendations/activities")
    public ApiResponse<RecommendationVO<ActivityCardVO>> recommendActivities(
            @RequestParam(value = "limit", defaultValue = "6") Integer limit,
            @RequestParam(value = "scene", defaultValue = "home") String scene) {
        List<ActivityCardVO> items = recommendationService.recommendActivities(
                currentUser.requireUserId(), limit, scene.toUpperCase());
        return ApiResponse.success(RecommendationVO.<ActivityCardVO>builder()
                .items(items)
                .degraded(true)
                .generatedAt(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "队伍推荐位", description = "只返回招募中且还缺人的队伍")
    @GetMapping("/recommendations/teams")
    public ApiResponse<RecommendationVO<TeamCardVO>> recommendTeams(
            @RequestParam(value = "limit", defaultValue = "4") Integer limit,
            @RequestParam(value = "activityId", required = false) Long activityId) {
        List<TeamCardVO> items = recommendationService.recommendTeams(
                currentUser.requireUserId(), limit, activityId);
        return ApiResponse.success(RecommendationVO.<TeamCardVO>builder()
                .items(items)
                .degraded(true)
                .generatedAt(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "推荐反馈埋点", description = "CLICK / ENROLL / IMPRESSION，幂等")
    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody FeedbackRequest request) {
        recommendationService.handleFeedback(currentUser.requireUserId(),
                request.getTargetType(), request.getTargetId(), request.getEvent());
        return ApiResponse.success("success", null);
    }

    @Operation(summary = "我的额度", description = "usedTokens / dailyTokenLimit / enabled")
    @GetMapping("/quota")
    public ApiResponse<QuotaVO> quota() {
        return ApiResponse.success(quotaService.quota(currentUser.requireUserId()));
    }
}
