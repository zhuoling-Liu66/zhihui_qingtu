package com.example.zhihuiqingtu.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.ai.engine.RecommendationEngine;
import com.example.zhihuiqingtu.ai.entity.AiToolAuditLog;
import com.example.zhihuiqingtu.ai.support.AiTextUtils;
import com.example.zhihuiqingtu.ai.support.AiToolEventSink;
import com.example.zhihuiqingtu.ai.vo.ActivityCardVO;
import com.example.zhihuiqingtu.ai.vo.PendingActionVO;
import com.example.zhihuiqingtu.ai.vo.TeamCardVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.entity.ActivityEnroll;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.ActivityTag;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.entity.UserInterestRel;
import com.example.zhihuiqingtu.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI 可调用的工具集。
 *
 * <p><b>身份不进工具参数</b>：构造时注入 {@code userId}，schema 里根本没有这个字段。
 * 这不是"约定"，是设计——模型无法被诱导去查别人，因为它连这个能力都不存在。
 *
 * <p><b>工具不直接写业务表</b>：唯一的"写"是 {@code propose_enroll}，
 * 它只登记待确认动作，真正报名发生在用户点确认之后。
 *
 * <p><b>不抛异常给框架</b>：所有失败都转成 {@code {ok:false, code, message}} 信封，
 * 让模型能读懂原因并用自然语言解释，而不是让前端弹 500。
 */
@Slf4j
public class AiToolSet {

    private final String userId;
    private final Long conversationId;
    private final AiToolEventSink sink;
    private final AiToolDependencies deps;

    public AiToolSet(String userId, Long conversationId, AiToolEventSink sink, AiToolDependencies deps) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.sink = sink == null ? AiToolEventSink.NOOP : sink;
        this.deps = deps;
    }

    // ══════════════════ 只读工具 ══════════════════

    @Tool(name = "search_activities",
            description = "按条件筛选校园活动。用户提到时间、类型、学分类型、地点、关键词时优先调用。"
                    + "只返回未来仍可报名、且用户尚未报名的活动。limit 默认 6，最多 6。")
    public String searchActivities(
            @ToolParam(description = "标题/描述/地点关键词，可为空", required = false) String keyword,
            @ToolParam(description = "活动类型：1第二课堂 2志愿服务 3运动健身 4兴趣团建", required = false) Integer type,
            @ToolParam(description = "标签ID列表，可为空", required = false) List<Long> tagIds,
            @ToolParam(description = "开始时间下界，格式 yyyy-MM-dd HH:mm 或 yyyy-MM-dd", required = false) String dateFrom,
            @ToolParam(description = "开始时间上界，格式 yyyy-MM-dd HH:mm 或 yyyy-MM-dd", required = false) String dateTo,
            @ToolParam(description = "学分类型：1创新创业 2智育 3德育 4体育 5美育 6劳育", required = false) Integer creditType,
            @ToolParam(description = "只看还有名额的，默认 true", required = false) Boolean onlyAvailable,
            @ToolParam(description = "返回条数，默认 6，最多 6", required = false) Integer limit) {

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", keyword);
        args.put("type", type);
        args.put("tagIds", tagIds);
        args.put("dateFrom", dateFrom);
        args.put("dateTo", dateTo);
        args.put("creditType", creditType);
        args.put("onlyAvailable", onlyAvailable);
        args.put("limit", limit);

        return run("search_activities", args, () -> {
            RecommendationEngine.ActivityFilter filter = new RecommendationEngine.ActivityFilter(
                    keyword,
                    type,
                    tagIds,
                    AiTextUtils.parseDateTime(dateFrom),
                    AiTextUtils.endOfDayIfDateOnly(dateTo),
                    creditType,
                    onlyAvailable == null || onlyAvailable,
                    true);
            // 最多 6 条：既够模型挑选 3 个推荐，又不至于让卡片把聊天窗口刷屏
            int safeLimit = limit == null ? 6 : Math.max(1, Math.min(limit, 6));
            List<RecommendationEngine.ActivityCandidate> candidates =
                    deps.recommendationEngine.recommendActivities(userId, filter, safeLimit);
            List<ActivityCardVO> cards = candidates.stream().map(deps.recommendationEngine::toActivityCard).toList();
            if (!cards.isEmpty()) {
                sink.activityCards(cards);
            }
            List<Map<String, Object>> items = new ArrayList<>(cards.size());
            for (ActivityCardVO card : cards) {
                items.add(compactActivity(card));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", items.size());
            data.put("items", items);
            String hint = items.isEmpty()
                    ? "没有符合条件的活动，可以放宽时间或类型再试"
                    : "共 " + items.size() + " 条，按匹配度排序";
            return new ToolOutcome(data, hint, items.size(), "返回 " + items.size() + " 条活动");
        });
    }

    @Tool(name = "get_activity_detail",
            description = "查询单个活动的详细信息，包括描述、名额、学分、标签。用于用户追问某个活动的细节。")
    public String getActivityDetail(
            @ToolParam(description = "活动ID", required = true) Long activityId) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("activityId", activityId);
        return run("get_activity_detail", args, () -> {
            if (activityId == null || activityId <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "活动ID不合法");
            }
            ActivityResponse activity = deps.activityService.getActivityDetail(activityId);
            ActivityMain raw = deps.activityMainRepository.selectById(activityId);
            Map<String, Object> item = compactActivityFromResponse(activity, raw);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activity", item);
            return new ToolOutcome(data, null, 1, "查询活动 " + activityId);
        });
    }

    @Tool(name = "search_teams",
            description = "查找还在招募中的活动队伍（还缺人的）。可按活动筛选，也可按队伍名关键词搜索。")
    public String searchTeams(
            @ToolParam(description = "限定某个活动ID，可为空", required = false) Long activityId,
            @ToolParam(description = "只看招募中的，默认 true", required = false) Boolean onlyRecruiting,
            @ToolParam(description = "队伍名关键词，可为空", required = false) String keyword,
            @ToolParam(description = "返回条数，默认 8，最多 10", required = false) Integer limit) {

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("activityId", activityId);
        args.put("onlyRecruiting", onlyRecruiting);
        args.put("keyword", keyword);
        args.put("limit", limit);

        return run("search_teams", args, () -> {
            int safeLimit = limit == null ? 8 : Math.max(1, Math.min(limit, 10));
            List<RecommendationEngine.TeamCandidate> candidates =
                    deps.recommendationEngine.recommendTeams(userId, activityId, safeLimit * 2);
            List<TeamCardVO> cards = candidates.stream()
                    .map(deps.recommendationEngine::toTeamCard)
                    .filter(card -> keyword == null || keyword.isBlank()
                            || (card.getTeamName() != null && card.getTeamName().contains(keyword)))
                    .limit(safeLimit)
                    .toList();
            if (!cards.isEmpty()) {
                sink.teamCards(cards);
            }
            List<Map<String, Object>> items = new ArrayList<>(cards.size());
            for (TeamCardVO card : cards) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("teamId", card.getTeamId());
                item.put("teamName", card.getTeamName());
                item.put("activityId", card.getActivityId());
                item.put("activityTitle", card.getActivityTitle());
                item.put("missing", card.getMissing());
                item.put("currentSize", card.getCurrentSize());
                item.put("maxSize", card.getMaxSize());
                item.put("leaderName", card.getLeaderName());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", items.size());
            data.put("items", items);
            String hint = items.isEmpty() ? "暂时没有还缺人的队伍" : "共 " + items.size() + " 个队伍";
            return new ToolOutcome(data, hint, items.size(), "返回 " + items.size() + " 个队伍");
        });
    }

    @Tool(name = "get_my_profile",
            description = "查询当前用户自己的画像：年级、专业、兴趣标签。用于个性化推荐。只能查自己。")
    public String getMyProfile() {
        return run("get_my_profile", Map.of(), () -> {
            UserInfo user = deps.userInfoRepository.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            List<Long> tagIds = deps.userInterestRelRepository.selectList(
                            new LambdaQueryWrapper<UserInterestRel>().eq(UserInterestRel::getUserId, userId))
                    .stream().map(UserInterestRel::getTagId).toList();
            List<String> tagNames = tagIds.isEmpty() ? List.of()
                    : deps.activityTagRepository.selectBatchIds(tagIds).stream()
                    .map(ActivityTag::getTagName).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("grade", user.getGrade());
            data.put("major", user.getMajor());
            data.put("interestTags", tagNames);
            // 刻意不返回手机号/邮箱：模型不需要，返回了就多一个泄露面
            return new ToolOutcome(data, tagNames.isEmpty() ? "用户还没设置兴趣标签，可以引导TA设置" : null,
                    1, "查询个人画像");
        });
    }

    @Tool(name = "get_my_enrollments",
            description = "查询当前用户自己报名过的活动，用于避免重复推荐、回答'我报了哪些活动'。")
    public String getMyEnrollments(
            @ToolParam(description = "报名状态：1已报名 3已签到 4已完成，可为空", required = false) Integer status,
            @ToolParam(description = "返回条数，默认 10，最多 20", required = false) Integer limit) {

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("status", status);
        args.put("limit", limit);

        return run("get_my_enrollments", args, () -> {
            int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 20));
            LambdaQueryWrapper<ActivityEnroll> wrapper = new LambdaQueryWrapper<ActivityEnroll>()
                    .eq(ActivityEnroll::getUserId, userId)
                    .eq(status != null, ActivityEnroll::getEnrollStatus, status)
                    .orderByDesc(ActivityEnroll::getCreateTime)
                    .last("LIMIT " + safeLimit);
            List<ActivityEnroll> enrollments = deps.activityEnrollRepository.selectList(wrapper);
            Map<Long, ActivityMain> activityMap = enrollments.isEmpty() ? Map.of()
                    : deps.activityMainRepository.selectBatchIds(
                            enrollments.stream().map(ActivityEnroll::getActivityId).toList())
                    .stream().collect(java.util.stream.Collectors.toMap(ActivityMain::getActivityId, a -> a));
            List<Map<String, Object>> items = new ArrayList<>();
            for (ActivityEnroll enrollment : enrollments) {
                ActivityMain activity = activityMap.get(enrollment.getActivityId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("enrollId", enrollment.getEnrollId());
                item.put("activityId", enrollment.getActivityId());
                item.put("activityTitle", activity == null ? null : activity.getActivityTitle());
                item.put("startTime", activity == null || activity.getStartTime() == null
                        ? null : activity.getStartTime().toString());
                item.put("enrollStatus", enrollment.getEnrollStatus());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", items.size());
            data.put("items", items);
            return new ToolOutcome(data, items.isEmpty() ? "用户还没有报名任何活动" : null,
                    items.size(), "返回 " + items.size() + " 条报名");
        });
    }

    @Tool(name = "get_my_credit_overview",
            description = "查询当前用户自己的第二课堂学分总览，用于推荐'补学分'的活动。")
    public String getMyCreditOverview() {
        return run("get_my_credit_overview", Map.of(), () -> {
            List<CreditRecord> records = deps.creditRecordRepository.selectList(
                    new LambdaQueryWrapper<CreditRecord>().eq(CreditRecord::getUserId, userId));
            Map<Integer, BigDecimal> byType = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            int pending = 0;
            for (CreditRecord record : records) {
                if (ReviewStatusEnum.APPROVED.getCode().equals(record.getVerifyStatus())) {
                    BigDecimal credit = record.getCredit() == null ? BigDecimal.ZERO : record.getCredit();
                    total = total.add(credit);
                    byType.merge(record.getCreditType(), credit, BigDecimal::add);
                } else if (ReviewStatusEnum.PENDING.getCode().equals(record.getVerifyStatus())) {
                    pending++;
                }
            }
            List<Map<String, Object>> types = new ArrayList<>();
            byType.forEach((type, credit) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("creditType", type);
                item.put("creditTypeName", creditTypeName(type));
                item.put("credit", credit);
                types.add(item);
            });
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalCredit", total);
            data.put("byType", types);
            data.put("pendingCount", pending);
            return new ToolOutcome(data, null, types.size(), "查询学分总览");
        });
    }

    // ══════════════════ 唯一"写"工具：只登记意图 ══════════════════

    @Tool(name = "propose_enroll",
            description = "在用户明确要求报名某个活动时，生成「确认报名」卡片。"
                    + "调用后只能说'确认后我就帮你报名'，绝对不能说'已报名成功'——"
                    + "真正的报名要等用户在卡片上点确认。")
    public String proposeEnroll(
            @ToolParam(description = "要报名的活动ID", required = true) Long activityId) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("activityId", activityId);
        return run("propose_enroll", args, () -> {
            if (activityId == null || activityId <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "活动ID不合法");
            }
            PendingActionVO action = deps.actionService.proposeEnroll(userId, conversationId, activityId);
            sink.pendingAction(action);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("actionId", action.getActionId());
            data.put("status", action.getStatus());
            data.put("expiresAt", action.getExpiresAt() == null ? null : action.getExpiresAt().toString());
            data.put("summary", action.getSummary());
            return new ToolOutcome(data, "已生成确认卡片，等待用户点击确认；此时尚未报名",
                    1, "生成待确认报名 " + activityId);
        });
    }

    // ══════════════════ 执行与审计 ══════════════════

    private String run(String toolName, Map<String, Object> args, Supplier<ToolOutcome> body) {
        long start = System.currentTimeMillis();
        sink.toolStarted(toolName);
        try {
            ToolOutcome outcome = body.get();
            long cost = System.currentTimeMillis() - start;
            sink.toolSucceeded(toolName, cost, outcome.count());
            audit(toolName, args, "SUCCESS", null, outcome.summary(), (int) cost);
            return toJson(okPayload(outcome));
        } catch (BusinessException exception) {
            long cost = System.currentTimeMillis() - start;
            sink.toolFailed(toolName, cost, exception.getMessage());
            audit(toolName, args, "FAILED", exception.getCode(), exception.getMessage(), (int) cost);
            return toJson(failPayload(exception.getCode(), exception.getMessage()));
        } catch (RuntimeException exception) {
            long cost = System.currentTimeMillis() - start;
            log.warn("工具 {} 执行异常: {}", toolName, exception.getMessage(), exception);
            sink.toolFailed(toolName, cost, "查询时出错了");
            audit(toolName, args, "FAILED", ErrorCode.SYSTEM_ERROR, exception.getClass().getSimpleName(), (int) cost);
            return toJson(failPayload(ErrorCode.SYSTEM_ERROR, "查询时出错了，请稍后再试"));
        }
    }

    private Map<String, Object> okPayload(ToolOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("data", outcome.data());
        if (outcome.hint() != null) {
            payload.put("hint", outcome.hint());
        }
        return payload;
    }

    private Map<String, Object> failPayload(Integer code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", false);
        payload.put("code", code);
        payload.put("message", message);
        payload.put("retryable", code != null && code >= 500);
        return payload;
    }

    private String toJson(Object payload) {
        String json;
        try {
            json = deps.objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            log.warn("工具结果序列化失败：{}", exception.getMessage());
            return "{\"ok\":false,\"code\":500,\"message\":\"内部序列化错误\"}";
        }
        return AiTextUtils.truncate(json, deps.properties.getTool().getMaxResultChars());
    }

    private void audit(String toolName, Map<String, Object> args, String status,
                       Integer errorCode, String summary, int latencyMs) {
        try {
            String argsJson;
            try {
                argsJson = deps.objectMapper.writeValueAsString(args);
            } catch (RuntimeException exception) {
                argsJson = "{}";
            }
            deps.auditLogRepository.insert(AiToolAuditLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .toolName(toolName)
                    .arguments(AiTextUtils.truncate(argsJson, 2000))
                    .status(status)
                    .errorCode(errorCode)
                    .resultSummary(AiTextUtils.truncate(summary, 400))
                    .latencyMs(latencyMs)
                    .build());
        } catch (RuntimeException exception) {
            // 审计写失败不能影响工具结果——否则一次数据库抖动就会让对话崩掉
            log.warn("工具审计写入失败：{}", exception.getMessage());
        }
    }

    // ══════════════════ 字段裁剪 ══════════════════

    private Map<String, Object> compactActivity(ActivityCardVO card) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", card.getActivityId());
        item.put("title", card.getActivityTitle());
        item.put("type", card.getActivityTypeName());
        item.put("tags", card.getTags() == null ? List.of()
                : card.getTags().stream().map(ActivityTagVO::getTagName).toList());
        item.put("start", card.getStartTime() == null ? null : card.getStartTime().toString());
        item.put("place", card.getActivityLocation());
        item.put("remaining", card.getRemaining());
        item.put("credit", card.getActivityCredit());
        item.put("creditType", card.getCreditTypeName());
        item.put("hours", card.getVolunteerHours());
        return item;
    }

    private Map<String, Object> compactActivityFromResponse(ActivityResponse activity, ActivityMain raw) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", activity.getActivityId());
        item.put("title", activity.getActivityTitle());
        item.put("desc", AiTextUtils.truncate(activity.getActivityDesc(), 400));
        item.put("type", RecommendationEngine.activityTypeName(activity.getActivityType()));
        item.put("tags", activity.getTags() == null ? List.of()
                : activity.getTags().stream().map(ActivityTagVO::getTagName).toList());
        item.put("start", activity.getStartTime() == null ? null : activity.getStartTime().toString());
        item.put("end", activity.getEndTime() == null ? null : activity.getEndTime().toString());
        item.put("place", activity.getActivityLocation());
        item.put("credit", activity.getActivityCredit());
        item.put("creditType", activity.getCreditType() == null
                ? null : RecommendationEngine.creditTypeName(activity.getCreditType()));
        item.put("hours", activity.getVolunteerHours());
        if (raw != null) {
            int max = raw.getMaxParticipant() == null ? 0 : raw.getMaxParticipant();
            int current = raw.getCurrentParticipant() == null ? 0 : raw.getCurrentParticipant();
            item.put("remaining", max > 0 ? Math.max(0, max - current) : null);
            item.put("maxParticipant", max > 0 ? max : null);
            item.put("status", ActivityStatusEnum.resolveDisplayStatus(raw.getActivityStatus(), raw.getEndTime()));
        }
        return item;
    }

    private String creditTypeName(Integer code) {
        for (CreditTypeEnum value : CreditTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getDesc();
            }
        }
        return "未知";
    }

    /**
     * 工具执行结果。
     *
     * @param data    给模型看的数据
     * @param hint    给模型的一句话提示（可为 null）
     * @param count   结果条数，用于流式事件展示
     * @param summary 审计摘要
     */
    private record ToolOutcome(Object data, String hint, int count, String summary) {
    }
}
