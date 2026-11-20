package com.example.zhihuiqingtu.activity.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.activity.entity.ActivityCreditTier;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.activity.entity.ActivityTag;
import com.example.zhihuiqingtu.activity.entity.ActivityTagRel;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动响应装配：把实体 + 标签 + 档位组装成前端 VO。
 *
 * <p>从原来的 ActivityServiceImpl 中拆出，供查询/命令两条链路复用。
 */
@Component
@RequiredArgsConstructor
public class ActivityAssembler {

    private final ActivityTagRelRepository activityTagRelRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityCreditTierRepository activityCreditTierRepository;

    public ActivityResponse toResponse(ActivityMain activityMain) {
        List<ActivityTagRel> relations = activityTagRelRepository.selectList(
                new LambdaQueryWrapper<ActivityTagRel>().eq(ActivityTagRel::getActivityId, activityMain.getActivityId())
        );
        List<Long> tagIds = relations.stream().map(ActivityTagRel::getTagId).toList();
        Map<Long, ActivityTag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : activityTagRepository.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(ActivityTag::getTagId, Function.identity()));

        List<ActivityTagVO> tags = relations.stream()
                .map(relation -> tagMap.get(relation.getTagId()))
                .filter(tag -> tag != null)
                .map(tag -> ActivityTagVO.builder()
                        .tagId(tag.getTagId())
                        .tagName(tag.getTagName())
                        .build())
                .toList();

        return ActivityResponse.builder()
                .activityId(activityMain.getActivityId())
                .publisherUserId(activityMain.getPublisherUserId())
                .activityTitle(activityMain.getActivityTitle())
                .activityDesc(activityMain.getActivityDesc())
                .activityType(activityMain.getActivityType())
                .activityCredit(activityMain.getActivityCredit())
                .creditType(activityMain.getCreditType())
                .creditMode(activityMain.getCreditMode())
                .volunteerHours(activityMain.getVolunteerHours())
                .volunteerMode(activityMain.getVolunteerMode())
                .coverUrl(activityMain.getCoverUrl())
                .startTime(activityMain.getStartTime())
                .endTime(activityMain.getEndTime())
                .activityLocation(activityMain.getActivityLocation())
                .maxParticipant(activityMain.getMaxParticipant())
                .currentParticipant(activityMain.getCurrentParticipant())
                .activityStatus(activityMain.getActivityStatus())
                // displayStatus 依赖当前时间，必须现算，不能缓存“结论”
                .displayStatus(ActivityStatusEnum.resolveDisplayStatus(
                        activityMain.getActivityStatus(), activityMain.getEndTime()))
                .signInEnabled(activityMain.getSignInEnabled())
                .reviewStatus(activityMain.getReviewStatus())
                .reviewReason(activityMain.getReviewReason())
                .reviewerUserId(activityMain.getReviewerUserId())
                .reviewTime(activityMain.getReviewTime())
                .tags(tags)
                .build();
    }

    public ActivityResponse toResponseWithTiers(ActivityMain activityMain) {
        ActivityResponse response = toResponse(activityMain);
        response.setCreditTiers(loadCreditTiers(activityMain.getActivityId()));
        return response;
    }

    private List<CreditTierVO> loadCreditTiers(Long activityId) {
        if (activityId == null) {
            return List.of();
        }
        return activityCreditTierRepository.selectList(new LambdaQueryWrapper<ActivityCreditTier>()
                        .eq(ActivityCreditTier::getActivityId, activityId)
                        .orderByAsc(ActivityCreditTier::getSortNo))
                .stream()
                .map(tier -> CreditTierVO.builder()
                        .tierId(tier.getTierId())
                        .credit(tier.getCredit())
                        .quota(tier.getQuota())
                        .build())
                .toList();
    }
}
