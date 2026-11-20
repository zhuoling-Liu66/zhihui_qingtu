package com.example.zhihuiqingtu.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.engine.RecommendationEngine;
import com.example.zhihuiqingtu.ai.entity.AiRecommendationLog;
import com.example.zhihuiqingtu.ai.repository.AiRecommendationLogRepository;
import com.example.zhihuiqingtu.ai.support.AiConstants;
import com.example.zhihuiqingtu.ai.vo.ActivityCardVO;
import com.example.zhihuiqingtu.ai.vo.TeamCardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.example.zhihuiqingtu.ai.engine.RecommendationEngine.ActivityCandidate;
import com.example.zhihuiqingtu.ai.engine.RecommendationEngine.ActivityFilter;

/**
 * 推荐位服务：把引擎的候选转成卡片、写曝光日志、处理点击/报名归因。
 *
 * <p><b>刻意不做缓存（v1）</b>：活动名额、状态变化频繁，缓存"卡片"会让用户看到
 * 已满员/已结束的活动——这类错误比"多查一次库"严重得多。
 * 等访问量真的上来，再按方案缓存"候选 ID + 分数"，渲染前回表校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final RecommendationEngine engine;
    private final AiRecommendationLogRepository logRepository;
    private final AiProperties properties;

    /**
     * 活动推荐位。
     */
    public List<ActivityCardVO> recommendActivities(String userId, int limit, String scene) {
        int safeLimit = normalizeLimit(limit, properties.getChat().getRecommendLimit());
        List<RecommendationEngine.ActivityCandidate> candidates =
                engine.recommendActivities(userId, RecommendationEngine.ActivityFilter.empty(), safeLimit);
        return candidates.stream().map(engine::toActivityCard).toList();
    }

    /**
     * 队伍推荐位。
     */
    public List<TeamCardVO> recommendTeams(String userId, int limit, Long activityId) {
        int safeLimit = normalizeLimit(limit, 4);
        return engine.recommendTeams(userId, activityId, safeLimit).stream()
                .map(engine::toTeamCard)
                .toList();
    }

    /**
     * 记录曝光。
     *
     * <p>写日志失败不能影响主流程：推荐位是"锦上添花"，埋点是"锦上添花的花"，
     * 为了一条统计记录让整个推荐接口 500 是本末倒置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void logImpressions(String userId,
                               Long conversationId,
                               Long messageId,
                               String scene,
                               List<ActivityCardVO> activities,
                               List<TeamCardVO> teams) {
        try {
            int rank = 1;
            if (activities != null) {
                for (ActivityCardVO card : activities) {
                    insertLog(userId, conversationId, messageId, AiConstants.TARGET_ACTIVITY,
                            card.getActivityId(), null, scene, card.getHotScore(), rank++, card.getReason());
                }
            }
            if (teams != null) {
                for (TeamCardVO card : teams) {
                    insertLog(userId, conversationId, messageId, AiConstants.TARGET_TEAM,
                            card.getActivityId(), card.getTeamId(), scene, null, rank++, card.getReason());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("写推荐曝光日志失败，忽略：{}", exception.getMessage());
        }
    }

    private void insertLog(String userId, Long conversationId, Long messageId, String targetType,
                           Long activityId, Long teamId, String scene, Double score, int rank, String reason) {
        logRepository.insert(AiRecommendationLog.builder()
                .userId(userId)
                .conversationId(conversationId)
                .messageId(messageId)
                .targetType(targetType)
                .activityId(activityId)
                .teamId(teamId)
                .scene(scene == null ? AiConstants.SCENE_HOME : scene)
                .score(score == null ? null : BigDecimal.valueOf(score))
                .rankNo(rank)
                .reason(reason)
                .abGroup("RULE")
                .isClicked(0)
                .isEnrolled(0)
                .build());
    }

    /**
     * 标记点击（幂等：只更新最近一条未点击记录）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markClicked(String userId, String targetType, Long targetId) {
        try {
            LambdaQueryWrapper<AiRecommendationLog> wrapper = new LambdaQueryWrapper<AiRecommendationLog>()
                    .eq(AiRecommendationLog::getUserId, userId)
                    .eq(AiRecommendationLog::getTargetType, targetType)
                    .eq(AiRecommendationLog::getIsClicked, 0)
                    .orderByDesc(AiRecommendationLog::getLogId)
                    .last("LIMIT 1");
            if (AiConstants.TARGET_ACTIVITY.equals(targetType)) {
                wrapper.eq(AiRecommendationLog::getActivityId, targetId);
            } else {
                wrapper.eq(AiRecommendationLog::getTeamId, targetId);
            }
            AiRecommendationLog row = logRepository.selectOne(wrapper);
            if (row != null) {
                row.setIsClicked(1);
                logRepository.updateById(row);
            }
        } catch (RuntimeException exception) {
            log.warn("标记点击失败：{}", exception.getMessage());
        }
    }

    /**
     * 报名成功后回填归因（由确认接口调用，不依赖前端上报）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markEnrolled(String userId, Long activityId) {
        try {
            AiRecommendationLog row = logRepository.selectOne(
                    new LambdaQueryWrapper<AiRecommendationLog>()
                            .eq(AiRecommendationLog::getUserId, userId)
                            .eq(AiRecommendationLog::getTargetType, AiConstants.TARGET_ACTIVITY)
                            .eq(AiRecommendationLog::getActivityId, activityId)
                            .eq(AiRecommendationLog::getIsEnrolled, 0)
                            .orderByDesc(AiRecommendationLog::getLogId)
                            .last("LIMIT 1"));
            if (row != null) {
                row.setIsEnrolled(1);
                logRepository.updateById(row);
            }
        } catch (RuntimeException exception) {
            log.warn("标记报名归因失败：{}", exception.getMessage());
        }
    }

    /** 供反馈接口使用：事件名 → 动作。 */
    @Transactional(rollbackFor = Exception.class)
    public void handleFeedback(String userId, String targetType, Long targetId, String event) {
        if (event == null) {
            return;
        }
        switch (event.toUpperCase()) {
            case "CLICK" -> markClicked(userId, targetType, targetId);
            case "ENROLL" -> {
                if (AiConstants.TARGET_ACTIVITY.equals(targetType)) {
                    markEnrolled(userId, targetId);
                }
            }
            case "IMPRESSION" -> {
                // 曝光已在推荐接口写入，这里不重复记
            }
            default -> log.debug("未知的反馈事件：{}", event);
        }
    }

    /** 记录一次"用户点了卡片"的时间戳，供后续扩展。 */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    private int normalizeLimit(int limit, int fallback) {
        if (limit <= 0) {
            return fallback;
        }
        return Math.min(limit, 20);
    }
}
