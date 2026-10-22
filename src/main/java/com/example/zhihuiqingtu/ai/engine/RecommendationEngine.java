package com.example.zhihuiqingtu.ai.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.vo.ActivityCardVO;
import com.example.zhihuiqingtu.ai.vo.TeamCardVO;
import com.example.zhihuiqingtu.entity.ActivityEnroll;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.ActivityTag;
import com.example.zhihuiqingtu.entity.ActivityTagRel;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.TeamMain;
import com.example.zhihuiqingtu.entity.TeamMember;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.entity.UserInterestRel;
import com.example.zhihuiqingtu.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.enums.TeamMemberStatusEnum;
import com.example.zhihuiqingtu.enums.TeamStatusEnum;
import com.example.zhihuiqingtu.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.TeamMainRepository;
import com.example.zhihuiqingtu.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.repository.UserInterestRelRepository;
import com.example.zhihuiqingtu.service.impl.HotActivityService;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推荐引擎（纯规则，不依赖任何模型）。
 *
 * <p><b>为什么先做规则</b>：模型会编造活动、会算错名额、会把已结束的活动推给用户。
 * 规则链路是"事实"的唯一来源，模型只在这之上做排序和措辞。
 * 同时它也是降级路径：LLM 不可用时，推荐位仍然有内容。
 *
 * <p>打分公式见 docs/AI助手技术方案.md §4.3，权重集中在本类的常量里，
 * 调参只改这里，不要把魔法数字散落各处。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationEngine {

    /** 权重：标签匹配。 */
    private static final double W_TAG = 0.40;
    /** 权重：热度。 */
    private static final double W_HOT = 0.20;
    /** 权重：时间契合。 */
    private static final double W_TIME = 0.15;
    /** 权重：类型偏好。 */
    private static final double W_TYPE = 0.10;
    /** 权重：学分缺口。 */
    private static final double W_CREDIT_GAP = 0.10;
    /** 权重：新鲜度。 */
    private static final double W_FRESH = 0.05;
    /** 同类多样性惩罚：每多一个同类型，扣 0.15。 */
    private static final double DIVERSITY_PENALTY = 0.15;
    /** 召回上限，防止一次拉太多。 */
    private static final int RECALL_LIMIT = 200;

    private final ActivityMainRepository activityMainRepository;
    private final ActivityTagRelRepository activityTagRelRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final UserInterestRelRepository userInterestRelRepository;
    private final TeamMainRepository teamMainRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserInfoRepository userInfoRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final HotActivityService hotActivityService;
    private final AiProperties properties;

    // ══════════════════ 活动推荐 ══════════════════

    /**
     * 召回 + 粗排，返回带分数与理由的候选活动（按分数降序）。
     */
    public List<ActivityCandidate> recommendActivities(String userId, ActivityFilter filter, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, properties.getChat().getMaxCandidates()));
        LocalDateTime now = LocalDateTime.now();

        Set<Long> enrolledIds = enrolledActivityIds(userId);
        Set<Long> interestTags = interestTagIds(userId);
        Set<Long> filterTags = filter != null && filter.tagIds() != null && !filter.tagIds().isEmpty()
                ? new LinkedHashSet<>(filter.tagIds())
                : Set.of();
        // 打分用的标签：优先用用户真实兴趣；用户没有兴趣标签时，退化为这次筛选指定的标签，
        // 否则"按标签筛选"的结果会因为 tagMatch 恒为 0 而看不出相关性。
        Set<Long> scoringTags = interestTags.isEmpty() ? filterTags : interestTags;

        LambdaQueryWrapper<ActivityMain> wrapper = new LambdaQueryWrapper<ActivityMain>()
                .eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                .gt(ActivityMain::getEndTime, now)
                .orderByAsc(ActivityMain::getStartTime)
                .last("LIMIT " + RECALL_LIMIT);

        if (filter != null) {
            if (filter.type() != null) {
                wrapper.eq(ActivityMain::getActivityType, filter.type());
            }
            if (filter.creditType() != null) {
                wrapper.eq(ActivityMain::getCreditType, filter.creditType());
            }
            if (filter.dateFrom() != null) {
                wrapper.ge(ActivityMain::getStartTime, filter.dateFrom());
            }
            if (filter.dateTo() != null) {
                wrapper.le(ActivityMain::getStartTime, filter.dateTo());
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String keyword = filter.keyword().trim();
                wrapper.and(w -> w.like(ActivityMain::getActivityTitle, keyword)
                        .or().like(ActivityMain::getActivityDesc, keyword)
                        .or().like(ActivityMain::getActivityLocation, keyword));
            }
            if (filter.onlyAvailable()) {
                wrapper.apply("(max_participant IS NULL OR current_participant < max_participant)");
            }
            if (filter.excludeEnrolled() && !enrolledIds.isEmpty()) {
                wrapper.notIn(ActivityMain::getActivityId, enrolledIds);
            }
        } else {
            wrapper.apply("(max_participant IS NULL OR current_participant < max_participant)");
            if (!enrolledIds.isEmpty()) {
                wrapper.notIn(ActivityMain::getActivityId, enrolledIds);
            }
        }

        // 标签是"关联表条件"，MyBatis-Plus 的 Wrapper 表达不了 exists 子查询的语义，
        // 所以先把符合标签的活动 ID 查出来，再 IN。候选集最多 200 条，这个代价可以接受。
        if (!filterTags.isEmpty()) {
            Set<Long> matched = activityIdsByTags(filterTags);
            if (matched.isEmpty()) {
                return List.of();
            }
            wrapper.in(ActivityMain::getActivityId, matched);
        }

        List<ActivityMain> activities = activityMainRepository.selectList(wrapper);
        if (activities.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ActivityTag>> tagMap = tagsByActivity(
                activities.stream().map(ActivityMain::getActivityId).toList());
        Map<Long, Double> hotMap = hotScores();
        Map<Integer, Double> typePreference = typePreference(userId);
        Set<Integer> ownedCreditTypes = ownedCreditTypes(userId);

        List<ActivityCandidate> candidates = new ArrayList<>(activities.size());
        for (ActivityMain activity : activities) {
            List<ActivityTag> tags = tagMap.getOrDefault(activity.getActivityId(), List.of());
            double hot = hotMap.getOrDefault(activity.getActivityId(), 0.0);
            double score = score(activity, tags, scoringTags, hot, typePreference, ownedCreditTypes, now);
            candidates.add(new ActivityCandidate(activity, tags, score,
                    buildReason(activity, tags, scoringTags, hot, ownedCreditTypes), hot));
        }
        candidates.sort(Comparator.comparingDouble(ActivityCandidate::score).reversed());

        // 同类多样性：避免 6 条全是"第二课堂"。分数已经排好序，这里做一次线性重排。
        return diversify(candidates, safeLimit);
    }

    private List<ActivityCandidate> diversify(List<ActivityCandidate> sorted, int limit) {
        List<ActivityCandidate> pool = new ArrayList<>(sorted);
        List<ActivityCandidate> picked = new ArrayList<>(limit);
        Map<Integer, Integer> typeCount = new HashMap<>();
        while (!pool.isEmpty() && picked.size() < limit) {
            ActivityCandidate best = null;
            double bestAdjusted = Double.NEGATIVE_INFINITY;
            for (ActivityCandidate candidate : pool) {
                int sameType = typeCount.getOrDefault(candidate.activity().getActivityType(), 0);
                double adjusted = candidate.score() - DIVERSITY_PENALTY * sameType;
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            pool.remove(best);
            picked.add(best);
            typeCount.merge(best.activity().getActivityType(), 1, Integer::sum);
        }
        return picked;
    }

    private double score(ActivityMain activity,
                         List<ActivityTag> tags,
                         Set<Long> userTagIds,
                         double hot,
                         Map<Integer, Double> typePreference,
                         Set<Integer> ownedCreditTypes,
                         LocalDateTime now) {
        double tagMatch = tagMatch(tags, userTagIds);
        double timeFit = timeFit(activity.getStartTime(), now);
        double typePref = typePreference.getOrDefault(activity.getActivityType(), 0.0);
        double creditGap = creditGap(activity, ownedCreditTypes);
        double fresh = freshness(activity.getCreateTime(), now);
        return W_TAG * tagMatch + W_HOT * hot + W_TIME * timeFit
                + W_TYPE * typePref + W_CREDIT_GAP * creditGap + W_FRESH * fresh;
    }

    /**
     * 二值余弦相似度：{@code |U∩A| / sqrt(|U|·|A|)}。
     * <p>不用 Jaccard 是因为它惩罚"用户标签多"，而标签多不代表兴趣不准。
     */
    private double tagMatch(List<ActivityTag> tags, Set<Long> userTagIds) {
        if (tags.isEmpty() || userTagIds.isEmpty()) {
            return 0;
        }
        long intersect = tags.stream().map(ActivityTag::getTagId).filter(userTagIds::contains).count();
        return intersect / Math.sqrt((double) tags.size() * userTagIds.size());
    }

    private double timeFit(LocalDateTime startTime, LocalDateTime now) {
        if (startTime == null) {
            return 0.3;
        }
        long days = Duration.between(now, startTime).toDays();
        if (days <= 0) {
            return 1.0;
        }
        if (days <= 3) {
            return 1.0;
        }
        if (days <= 7) {
            return 0.8;
        }
        if (days <= 14) {
            return 0.55;
        }
        return 0.3;
    }

    private double freshness(LocalDateTime createTime, LocalDateTime now) {
        if (createTime == null) {
            return 0.3;
        }
        long hours = Duration.between(createTime, now).toHours();
        if (hours <= 24) {
            return 1.0;
        }
        if (hours <= 72) {
            return 0.7;
        }
        return 0.4;
    }

    private double creditGap(ActivityMain activity, Set<Integer> ownedCreditTypes) {
        Integer creditType = activity.getCreditType();
        if (creditType == null || activity.getActivityCredit() == null) {
            return 0;
        }
        return ownedCreditTypes.contains(creditType) ? 0 : 1;
    }

    /**
     * 生成推荐理由。
     *
     * <p>理由必须可追溯到真实特征——这是"可解释推荐"，也是防止模型胡说的一道约束：
     * 即使后面交给 LLM 润色，事实骨架也来自这里。
     */
    private String buildReason(ActivityMain activity,
                               List<ActivityTag> tags,
                               Set<Long> userTagIds,
                               double hot,
                               Set<Integer> ownedCreditTypes) {
        List<ActivityTag> matched = tags.stream().filter(t -> userTagIds.contains(t.getTagId())).toList();
        if (!matched.isEmpty()) {
            String names = matched.stream().limit(2).map(ActivityTag::getTagName)
                    .collect(Collectors.joining("、"));
            return "和你关注的「" + names + "」兴趣一致";
        }
        if (activity.getCreditType() != null && !ownedCreditTypes.contains(activity.getCreditType())) {
            return "你还没拿到「" + creditTypeName(activity.getCreditType()) + "」学分，这个正好可以补";
        }
        if (hot >= 0.6) {
            return "最近很热门，报名的人不少";
        }
        if (activity.getActivityLocation() != null && !activity.getActivityLocation().isBlank()) {
            return "时间合适，地点在" + activity.getActivityLocation();
        }
        return "时间合适，可以看看";
    }

    // ══════════════════ 队伍推荐 ══════════════════

    /**
     * 推荐"还缺人"的队伍。
     */
    public List<TeamCandidate> recommendTeams(String userId, Long activityId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        Set<Long> myTeamIds = teamMemberRepository.selectList(
                        new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId))
                .stream()
                .filter(member -> member.getMemberStatus() != null
                        && member.getMemberStatus() != TeamMemberStatusEnum.EXITED.getCode()
                        && member.getMemberStatus() != TeamMemberStatusEnum.REJECTED.getCode())
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<TeamMain> wrapper = new LambdaQueryWrapper<TeamMain>()
                .eq(TeamMain::getTeamStatus, TeamStatusEnum.RECRUITING.getCode())
                .apply("current_size < max_size")
                .orderByDesc(TeamMain::getCreateTime)
                .last("LIMIT " + Math.max(20, safeLimit * 4));
        if (activityId != null) {
            wrapper.eq(TeamMain::getActivityId, activityId);
        }
        if (!myTeamIds.isEmpty()) {
            wrapper.notIn(TeamMain::getTeamId, myTeamIds);
        }
        List<TeamMain> teams = teamMainRepository.selectList(wrapper);
        if (teams.isEmpty()) {
            return List.of();
        }

        // 队伍关联的活动必须还没结束，否则推给用户也报不了名。
        //
        // 两个必须处理的边界：
        //   1. selectBatchIds 传空集合会生成 "IN ()"，MySQL 直接语法错误 —— 必须短路；
        //   2. 历史数据里团队可能没有关联活动（activity_id 为 null），
        //      这类队伍仍然值得推荐（它可能是通用组队），用中性的活动分 0.5 参与排序。
        Set<Long> activityIds = teams.stream().map(TeamMain::getActivityId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ActivityMain> activityMap = activityIds.isEmpty() ? Map.of()
                : activityMainRepository.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, Function.identity()));
        Map<String, String> leaderNames = leaderNames(teams.stream().map(TeamMain::getLeaderUserId).toList());

        List<TeamCandidate> candidates = new ArrayList<>();
        for (TeamMain team : teams) {
            ActivityMain activity = team.getActivityId() == null ? null : activityMap.get(team.getActivityId());
            if (activity != null && (activity.getEndTime() == null
                    || !activity.getEndTime().isAfter(LocalDateTime.now()))) {
                continue;
            }
            // 有关联活动但查不到（活动被删）：跳过，避免推一个点进去 404 的队
            if (team.getActivityId() != null && activity == null) {
                continue;
            }
            int max = team.getMaxSize() == null ? 0 : team.getMaxSize();
            int current = team.getCurrentSize() == null ? 0 : team.getCurrentSize();
            if (max <= current) {
                continue;
            }
            double vacancy = max == 0 ? 0 : (double) (max - current) / max;
            double activityScore = activity == null ? 0.5 : 1.0;
            double score = 0.5 * activityScore + 0.3 * vacancy
                    + 0.2 * freshness(team.getCreateTime(), LocalDateTime.now());
            String reason = "还差 " + (max - current) + " 人"
                    + (activity == null ? "" : "，活动还没结束");
            candidates.add(new TeamCandidate(team, activity == null ? null : activity.getActivityTitle(),
                    leaderNames.get(team.getLeaderUserId()), score, reason));
        }
        candidates.sort(Comparator.comparingDouble(TeamCandidate::score).reversed());
        return candidates.size() > safeLimit ? candidates.subList(0, safeLimit) : candidates;
    }

    // ══════════════════ 卡片装配 ══════════════════

    public ActivityCardVO toActivityCard(ActivityCandidate candidate) {
        ActivityMain activity = candidate.activity();
        Integer max = activity.getMaxParticipant();
        int current = activity.getCurrentParticipant() == null ? 0 : activity.getCurrentParticipant();
        boolean limited = max != null && max > 0;
        return ActivityCardVO.builder()
                .activityId(activity.getActivityId())
                .activityTitle(activity.getActivityTitle())
                .activityType(activity.getActivityType())
                .activityTypeName(activityTypeName(activity.getActivityType()))
                .coverUrl(activity.getCoverUrl())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .activityLocation(activity.getActivityLocation())
                .remaining(limited ? Math.max(0, max - current) : null)
                .limited(limited)
                .activityCredit(activity.getActivityCredit())
                .creditType(activity.getCreditType())
                .creditTypeName(activity.getCreditType() == null ? null : creditTypeName(activity.getCreditType()))
                .volunteerHours(activity.getVolunteerHours())
                .tags(candidate.tags().stream()
                        .map(tag -> ActivityTagVO.builder().tagId(tag.getTagId()).tagName(tag.getTagName()).build())
                        .toList())
                .reason(candidate.reason())
                .hotScore(round(candidate.hot()))
                .build();
    }

    public TeamCardVO toTeamCard(TeamCandidate candidate) {
        TeamMain team = candidate.team();
        int max = team.getMaxSize() == null ? 0 : team.getMaxSize();
        int current = team.getCurrentSize() == null ? 0 : team.getCurrentSize();
        return TeamCardVO.builder()
                .teamId(team.getTeamId())
                .teamName(team.getTeamName())
                .activityId(team.getActivityId())
                .activityTitle(candidate.activityTitle())
                .leaderUserId(team.getLeaderUserId())
                .leaderName(candidate.leaderName())
                .maxSize(max)
                .currentSize(current)
                .missing(Math.max(0, max - current))
                .teamStatus(team.getTeamStatus())
                .teamStatusName(teamStatusName(team.getTeamStatus()))
                .reason(candidate.reason())
                .build();
    }

    // ══════════════════ 数据查询 ══════════════════

    private Set<Long> enrolledActivityIds(String userId) {
        return activityEnrollRepository.selectList(
                        new LambdaQueryWrapper<ActivityEnroll>().eq(ActivityEnroll::getUserId, userId))
                .stream().map(ActivityEnroll::getActivityId).collect(Collectors.toSet());
    }

    public Set<Long> interestTagIds(String userId) {
        return userInterestRelRepository.selectList(
                        new LambdaQueryWrapper<UserInterestRel>().eq(UserInterestRel::getUserId, userId))
                .stream().map(UserInterestRel::getTagId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> activityIdsByTags(Collection<Long> tagIds) {
        return activityTagRelRepository.selectList(
                        new LambdaQueryWrapper<ActivityTagRel>().in(ActivityTagRel::getTagId, tagIds))
                .stream().map(ActivityTagRel::getActivityId).collect(Collectors.toSet());
    }

    public Map<Long, List<ActivityTag>> tagsByActivity(Collection<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        List<ActivityTagRel> relations = activityTagRelRepository.selectList(
                new LambdaQueryWrapper<ActivityTagRel>().in(ActivityTagRel::getActivityId, activityIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = relations.stream().map(ActivityTagRel::getTagId).collect(Collectors.toSet());
        Map<Long, ActivityTag> tagMap = activityTagRepository.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(ActivityTag::getTagId, Function.identity()));
        Map<Long, List<ActivityTag>> result = new HashMap<>();
        for (ActivityTagRel relation : relations) {
            ActivityTag tag = tagMap.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getActivityId(), key -> new ArrayList<>()).add(tag);
            }
        }
        return result;
    }

    /** 热度的归一化：用排名而不是绝对报名数，避免"第一名的绝对值碾压一切"。 */
    private Map<Long, Double> hotScores() {
        List<Long> ids = hotActivityService.listHotActivityIds(100);
        Map<Long, Double> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            result.put(ids.get(i), 1.0 - (double) i / Math.max(1, ids.size()));
        }
        return result;
    }

    /** 用户历史报名的类型分布（0~1）。 */
    private Map<Integer, Double> typePreference(String userId) {
        Set<Long> enrolled = enrolledActivityIds(userId);
        if (enrolled.isEmpty()) {
            return Map.of();
        }
        List<ActivityMain> activities = activityMainRepository.selectBatchIds(enrolled);
        if (activities.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> counts = activities.stream()
                .map(ActivityMain::getActivityType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        Map<Integer, Double> result = new HashMap<>();
        counts.forEach((type, count) -> result.put(type, (double) count / total));
        return result;
    }

    /** 用户已经审核通过的学分类型。 */
    private Set<Integer> ownedCreditTypes(String userId) {
        return creditRecordRepository.selectList(
                        new LambdaQueryWrapper<CreditRecord>()
                                .eq(CreditRecord::getUserId, userId)
                                .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.APPROVED.getCode()))
                .stream().map(CreditRecord::getCreditType).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Map<String, String> leaderNames(Collection<String> leaderIds) {
        Set<String> ids = leaderIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, UserInfo::getUserName, (a, b) -> a));
    }

    // ══════════════════ 枚举名 ══════════════════

    public static String activityTypeName(Integer code) {
        for (ActivityTypeEnum value : ActivityTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getDesc();
            }
        }
        return "未知类型";
    }

    public static String creditTypeName(Integer code) {
        for (CreditTypeEnum value : CreditTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getDesc();
            }
        }
        return "未知学分";
    }

    public static String teamStatusName(Integer code) {
        for (TeamStatusEnum value : TeamStatusEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getDesc();
            }
        }
        return "未知状态";
    }

    private static Double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    // ══════════════════ 值对象 ══════════════════

    /**
     * 活动筛选条件。
     *
     * <p>由模型把自然语言解析成这些字段，再由 Java 拼 SQL——**不让模型生成 SQL**。
     */
    public record ActivityFilter(String keyword,
                                 Integer type,
                                 List<Long> tagIds,
                                 LocalDateTime dateFrom,
                                 LocalDateTime dateTo,
                                 Integer creditType,
                                 boolean onlyAvailable,
                                 boolean excludeEnrolled) {

        public static ActivityFilter empty() {
            return new ActivityFilter(null, null, null, null, null, null, true, true);
        }
    }

    /** 候选活动（活动实体 + 标签 + 分数 + 理由 + 热度）。 */
    public record ActivityCandidate(ActivityMain activity,
                                    List<ActivityTag> tags,
                                    double score,
                                    String reason,
                                    double hot) {
    }

    /** 候选队伍。 */
    public record TeamCandidate(TeamMain team,
                                String activityTitle,
                                String leaderName,
                                double score,
                                String reason) {
    }
}
