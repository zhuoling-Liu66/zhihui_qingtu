package com.example.zhihuiqingtu.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.entity.AiPendingAction;
import com.example.zhihuiqingtu.ai.repository.AiPendingActionRepository;
import com.example.zhihuiqingtu.ai.support.AiConstants;
import com.example.zhihuiqingtu.ai.vo.ActionConfirmVO;
import com.example.zhihuiqingtu.ai.vo.PendingActionVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.dto.enrollment.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.entity.ActivityEnroll;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.service.EnrollmentService;
import com.example.zhihuiqingtu.vo.enrollment.EnrollmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认动作：写操作的"两阶段确认"。
 *
 * <p>第一阶段由模型触发 {@link #proposeEnroll}，<b>只登记意图</b>；
 * 第二阶段由用户在界面上点确认，触发 {@link #confirm} 才真正调用
 * {@link EnrollmentService#createEnrollment}。
 *
 * <p><b>这个设计的意义</b>：模型没有被授予"写库"的能力。即使活动描述里藏了
 * 提示词注入，最坏结果也只是生成一堆待确认卡片，不会真的替用户报名。
 *
 * <p><b>并发</b>：确认接口用"条件 UPDATE 抢占"来防止两个请求同时执行同一个动作；
 * 真正的重复报名由 {@code uk_activity_enroll_user} 唯一索引兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiActionService {

    private final AiPendingActionRepository actionRepository;
    private final ActivityMainRepository activityMainRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final EnrollmentService enrollmentService;
    private final AiRecommendationService recommendationService;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    // ══════════════════ 第一阶段：登记意图 ══════════════════

    /**
     * 登记"报名某活动"的待确认动作。
     *
     * @return 待确认动作（<b>不代表已报名</b>）
     */
    @Transactional(rollbackFor = Exception.class)
    public PendingActionVO proposeEnroll(String userId, Long conversationId, Long activityId) {
        ActivityMain activity = activityMainRepository.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        if (!ActivityStatusEnum.PUBLISHED.getCode().equals(activity.getActivityStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该活动当前不可报名");
        }
        if (activity.getEndTime() != null && !activity.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该活动已结束");
        }
        int max = activity.getMaxParticipant() == null ? 0 : activity.getMaxParticipant();
        int current = activity.getCurrentParticipant() == null ? 0 : activity.getCurrentParticipant();
        if (max > 0 && current >= max) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动报名人数已满");
        }
        boolean enrolled = activityEnrollRepository.selectCount(
                new LambdaQueryWrapper<ActivityEnroll>()
                        .eq(ActivityEnroll::getUserId, userId)
                        .eq(ActivityEnroll::getActivityId, activityId)) > 0;
        if (enrolled) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已报名该活动");
        }

        // 同一用户同一时刻只允许一个待确认动作：否则模型可能一口气生成 5 张卡片，
        // 用户逐张点确认就变成了"批量报名"，这属于产品事故。
        AiPendingAction pending = findPending(userId);
        if (pending != null) {
            if (expired(pending)) {
                markExpired(pending);
            } else if (activityId.equals(readActivityId(pending))) {
                // 同一个活动重复发起：幂等返回已有动作，不新建
                return toVO(pending);
            } else {
                throw new BusinessException(ErrorCode.CONFLICT, "已有待确认的报名，请先在卡片上处理");
            }
        }

        LocalDateTime expiresAt = LocalDateTime.now().plus(properties.getAction().getTtl());
        AiPendingAction action = AiPendingAction.builder()
                .actionId(AiConstants.ACTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .conversationId(conversationId)
                .type(AiConstants.TYPE_ENROLL_ACTIVITY)
                .arguments("{\"activityId\":" + activityId + "}")
                .summary(buildSummary(activity))
                .status(AiConstants.ACTION_PENDING)
                .expiresAt(expiresAt)
                .build();
        actionRepository.insert(action);
        log.info("生成待确认报名动作 actionId={} userId={} activityId={}",
                action.getActionId(), com.example.zhihuiqingtu.ai.support.AiTextUtils.mask(userId), activityId);
        return toVO(action);
    }

    // ══════════════════ 第二阶段：用户确认 ══════════════════

    /**
     * 执行待确认动作。
     *
     * @param userId   当前登录用户（必须与动作归属一致）
     * @param actionId 动作ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionConfirmVO confirm(String userId, String actionId) {
        AiPendingAction action = getOwned(userId, actionId);

        if (AiConstants.ACTION_EXECUTED.equals(action.getStatus())) {
            // 幂等：重复点确认返回原结果，不重复报名（前端也可能因网络重试）
            return idempotentResult(userId, action);
        }
        if (AiConstants.ACTION_CANCELLED.equals(action.getStatus())
                || AiConstants.ACTION_FAILED.equals(action.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该操作已失效，请重新发起");
        }
        if (expired(action)) {
            throw new BusinessException(ErrorCode.CONFLICT, "确认已过期，请重新发起");
        }

        // 条件 UPDATE 抢占：只有把 PENDING 改成 EXECUTED 成功的那个请求才能继续执行。
        // 两个请求同时点确认时，后到的 affected rows = 0，直接走下面的分支。
        int claimed = actionRepository.update(null,
                new LambdaUpdateWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getActionId, actionId)
                        .eq(AiPendingAction::getStatus, AiConstants.ACTION_PENDING)
                        .gt(AiPendingAction::getExpiresAt, LocalDateTime.now())
                        .set(AiPendingAction::getStatus, AiConstants.ACTION_EXECUTED)
                        .set(AiPendingAction::getExecutedAt, LocalDateTime.now()));
        if (claimed == 0) {
            AiPendingAction latest = getOwned(userId, actionId);
            if (AiConstants.ACTION_EXECUTED.equals(latest.getStatus())) {
                return idempotentResult(userId, latest);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "该操作已被处理或已过期");
        }

        Long activityId = readActivityId(action);
        if (activityId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "动作参数损坏");
        }

        // ★ 关键：调用既有报名 Service，而不是自己拼 INSERT。
        // 它内部已经处理了 Redis 库存闸门、数据库条件扣减、唯一索引兜底与缓存失效。
        CreateEnrollmentRequest enrollRequest = new CreateEnrollmentRequest();
        enrollRequest.setActivityId(activityId);
        EnrollmentResponse enrollment = enrollmentService.createEnrollment(enrollRequest);

        // 报名成功后回填推荐归因，不依赖前端再报一次
        recommendationService.markEnrolled(userId, activityId);

        ActivityMain activity = activityMainRepository.selectById(activityId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enrollId", enrollment.getEnrollId());
        result.put("activityId", activityId);
        result.put("activityTitle", enrollment.getActivityTitle());
        result.put("enrollStatus", enrollment.getEnrollStatus());
        if (activity != null) {
            result.put("currentParticipant", activity.getCurrentParticipant());
        }

        // 状态已在抢占时写入；这里补一条结果说明
        actionRepository.update(null,
                new LambdaUpdateWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getActionId, actionId)
                        .set(AiPendingAction::getResultMessage, "报名成功"));
        log.info("待确认动作执行成功 actionId={} userId={} activityId={}",
                actionId, com.example.zhihuiqingtu.ai.support.AiTextUtils.mask(userId), activityId);

        return ActionConfirmVO.builder()
                .actionId(actionId)
                .status(AiConstants.ACTION_EXECUTED)
                .executedAt(LocalDateTime.now())
                .result(result)
                .build();
    }

    /**
     * 取消待确认动作。
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionConfirmVO cancel(String userId, String actionId) {
        AiPendingAction action = getOwned(userId, actionId);
        if (AiConstants.ACTION_EXECUTED.equals(action.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已报名成功，如需退出请使用「取消报名」");
        }
        if (AiConstants.ACTION_CANCELLED.equals(action.getStatus())) {
            return ActionConfirmVO.builder()
                    .actionId(actionId).status(AiConstants.ACTION_CANCELLED).build();
        }
        if (expired(action)) {
            markExpired(action);
            throw new BusinessException(ErrorCode.CONFLICT, "该操作已过期");
        }
        action.setStatus(AiConstants.ACTION_CANCELLED);
        actionRepository.updateById(action);
        return ActionConfirmVO.builder()
                .actionId(actionId)
                .status(AiConstants.ACTION_CANCELLED)
                .build();
    }

    // ══════════════════ 内部 ══════════════════

    private AiPendingAction getOwned(String userId, String actionId) {
        AiPendingAction action = actionRepository.selectById(actionId);
        if (action == null || !userId.equals(action.getUserId())) {
            // 不区分"不存在"和"不是你的"：区分了就等于告诉攻击者哪些 ID 存在
            throw new BusinessException(ErrorCode.NOT_FOUND, "操作不存在");
        }
        return action;
    }

    private AiPendingAction findPending(String userId) {
        return actionRepository.selectOne(new LambdaQueryWrapper<AiPendingAction>()
                .eq(AiPendingAction::getUserId, userId)
                .eq(AiPendingAction::getStatus, AiConstants.ACTION_PENDING)
                .orderByDesc(AiPendingAction::getCreateTime)
                .last("LIMIT 1"));
    }

    private boolean expired(AiPendingAction action) {
        return action.getExpiresAt() == null || action.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void markExpired(AiPendingAction action) {
        action.setStatus(AiConstants.ACTION_EXPIRED);
        actionRepository.updateById(action);
    }

    private Long readActivityId(AiPendingAction action) {
        if (action.getArguments() == null || action.getArguments().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(action.getArguments());
            JsonNode idNode = node.get("activityId");
            return idNode == null || idNode.isNull() ? null : idNode.asLong();
        } catch (RuntimeException exception) {
            log.warn("解析动作参数失败 actionId={}: {}", action.getActionId(), exception.getMessage());
            return null;
        }
    }

    private ActionConfirmVO idempotentResult(String userId, AiPendingAction action) {
        Long activityId = readActivityId(action);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityId", activityId);
        if (activityId != null) {
            ActivityMain activity = activityMainRepository.selectById(activityId);
            if (activity != null) {
                result.put("activityTitle", activity.getActivityTitle());
                result.put("currentParticipant", activity.getCurrentParticipant());
            }
            ActivityEnroll enrollment = activityEnrollRepository.selectOne(
                    new LambdaQueryWrapper<ActivityEnroll>()
                            .eq(ActivityEnroll::getUserId, userId)
                            .eq(ActivityEnroll::getActivityId, activityId)
                            .last("LIMIT 1"));
            if (enrollment != null) {
                result.put("enrollId", enrollment.getEnrollId());
                result.put("enrollStatus", enrollment.getEnrollStatus());
            }
        }
        return ActionConfirmVO.builder()
                .actionId(action.getActionId())
                .status(AiConstants.ACTION_EXECUTED)
                .executedAt(action.getExecutedAt())
                .result(result)
                .build();
    }

    private PendingActionVO toVO(AiPendingAction action) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("text", action.getSummary());
        Long activityId = readActivityId(action);
        if (activityId != null) {
            ActivityMain activity = activityMainRepository.selectById(activityId);
            if (activity != null) {
                summary.put("activityId", activity.getActivityId());
                summary.put("activityTitle", activity.getActivityTitle());
                summary.put("startTime", activity.getStartTime());
                summary.put("activityLocation", activity.getActivityLocation());
                int max = activity.getMaxParticipant() == null ? 0 : activity.getMaxParticipant();
                int current = activity.getCurrentParticipant() == null ? 0 : activity.getCurrentParticipant();
                summary.put("remaining", max > 0 ? Math.max(0, max - current) : null);
            }
        }
        return PendingActionVO.builder()
                .actionId(action.getActionId())
                .type(action.getType())
                .status(action.getStatus())
                .expiresAt(action.getExpiresAt())
                .summary(summary)
                .confirmUrl("/api/ai/actions/" + action.getActionId() + "/confirm")
                .cancelUrl("/api/ai/actions/" + action.getActionId() + "/cancel")
                .build();
    }

    private String buildSummary(ActivityMain activity) {
        StringBuilder builder = new StringBuilder(activity.getActivityTitle());
        if (activity.getStartTime() != null) {
            builder.append(" · ").append(activity.getStartTime());
        }
        if (activity.getActivityLocation() != null && !activity.getActivityLocation().isBlank()) {
            builder.append(" · ").append(activity.getActivityLocation());
        }
        return com.example.zhihuiqingtu.ai.support.AiTextUtils.truncate(builder.toString(), 200);
    }
}
