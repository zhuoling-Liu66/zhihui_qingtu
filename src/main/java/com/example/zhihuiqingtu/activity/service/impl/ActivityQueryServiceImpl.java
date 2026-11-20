package com.example.zhihuiqingtu.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.activity.dto.ActivityQueryRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.activity.entity.ActivityTag;
import com.example.zhihuiqingtu.activity.entity.ActivityTagRel;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.activity.service.ActivityQueryService;
import com.example.zhihuiqingtu.activity.support.ActivityAssembler;
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.support.HotActivityService;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityStockResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动只读查询实现：列表、详情、标签、热门、剩余名额。
 */
@Service
@RequiredArgsConstructor
public class ActivityQueryServiceImpl implements ActivityQueryService {

    private final ActivityMainRepository activityMainRepository;
    private final ActivityTagRelRepository activityTagRelRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityCacheService activityCacheService;
    private final HotActivityService hotActivityService;
    private final ActivityAssembler activityAssembler;
    private final CurrentUser currentUser;

    @Override
    public PageResponse<ActivityResponse> queryActivities(ActivityQueryRequest request) {
        LambdaQueryWrapper<ActivityMain> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            List<Long> tagMatchedActivityIds = findActivityIdsByTagName(keyword);
            queryWrapper.and(wrapper -> {
                wrapper.like(ActivityMain::getActivityTitle, keyword)
                        .or().like(ActivityMain::getActivityDesc, keyword)
                        .or().like(ActivityMain::getActivityLocation, keyword);
                if (!tagMatchedActivityIds.isEmpty()) {
                    wrapper.or().in(ActivityMain::getActivityId, tagMatchedActivityIds);
                }
            });
        }

        queryWrapper.eq(request.getActivityType() != null, ActivityMain::getActivityType, request.getActivityType())
                .eq(StringUtils.hasText(request.getPublisherUserId()),
                        ActivityMain::getPublisherUserId, request.getPublisherUserId());

        String currentUserId = currentUser.userId().orElse(null);
        boolean queryingSelf = StringUtils.hasText(request.getPublisherUserId())
                && request.getPublisherUserId().equals(currentUserId);
        if (queryingSelf) {
            queryWrapper.eq(request.getReviewStatus() != null,
                    ActivityMain::getReviewStatus, request.getReviewStatus());
        } else {
            queryWrapper.eq(ActivityMain::getReviewStatus, ReviewStatusEnum.APPROVED.getCode());
        }

        applySectionFilter(queryWrapper, request.getSection());

        if (request.getActivityStatus() != null) {
            if (ActivityStatusEnum.FINISHED.getCode().equals(request.getActivityStatus())) {
                queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                        .lt(ActivityMain::getEndTime, LocalDateTime.now());
            } else if (ActivityStatusEnum.PUBLISHED.getCode().equals(request.getActivityStatus())) {
                queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode());
            } else if (ActivityStatusEnum.CANCELED.getCode().equals(request.getActivityStatus())) {
                queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.CANCELED.getCode());
            }
        }

        queryWrapper.orderByDesc(ActivityMain::getCreateTime);

        Page<ActivityMain> page = activityMainRepository.selectPage(
                new Page<>(request.getPageNo() + 1L, request.getPageSize()),
                queryWrapper
        );
        Page<ActivityResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream()
                .map(activityAssembler::toResponse)
                .toList());
        return PageResponse.from(responsePage);
    }

    private void applySectionFilter(LambdaQueryWrapper<ActivityMain> wrapper, String section) {
        if (!StringUtils.hasText(section)) {
            return;
        }
        Integer secondClass = ActivityTypeEnum.SECOND_CLASS.getCode();
        Integer volunteer = ActivityTypeEnum.VOLUNTEER.getCode();
        Integer sports = ActivityTypeEnum.SPORTS.getCode();
        Integer interest = ActivityTypeEnum.INTEREST.getCode();

        switch (section) {
            case "second-class":
                wrapper.eq(ActivityMain::getActivityType, secondClass)
                        .gt(ActivityMain::getActivityCredit, BigDecimal.ZERO);
                break;
            case "volunteer":
                wrapper.eq(ActivityMain::getActivityType, volunteer);
                break;
            case "innovation":
                wrapper.eq(ActivityMain::getActivityType, secondClass)
                        .eq(ActivityMain::getCreditType, 1);
                break;
            case "intellectual":
                wrapper.eq(ActivityMain::getActivityType, secondClass)
                        .eq(ActivityMain::getCreditType, 2);
                break;
            case "moral":
                wrapper.eq(ActivityMain::getActivityType, secondClass)
                        .eq(ActivityMain::getCreditType, 3);
                break;
            case "physical":
                wrapper.and(w -> w
                        .nested(n -> n.eq(ActivityMain::getActivityType, secondClass)
                                .eq(ActivityMain::getCreditType, 4))
                        .or()
                        .eq(ActivityMain::getActivityType, sports));
                break;
            case "aesthetic":
                wrapper.eq(ActivityMain::getActivityType, secondClass)
                        .eq(ActivityMain::getCreditType, 5);
                break;
            case "labor":
                wrapper.eq(ActivityMain::getActivityType, secondClass)
                        .eq(ActivityMain::getCreditType, 6);
                break;
            case "sports":
                wrapper.eq(ActivityMain::getActivityType, sports);
                break;
            case "interest":
                wrapper.eq(ActivityMain::getActivityType, interest);
                break;
            case "team":
                wrapper.in(ActivityMain::getActivityType, sports, interest);
                break;
            default:
                break;
        }
    }

    private List<Long> findActivityIdsByTagName(String keyword) {
        List<Long> tagIds = activityTagRepository.selectList(new LambdaQueryWrapper<ActivityTag>()
                        .like(ActivityTag::getTagName, keyword))
                .stream()
                .map(ActivityTag::getTagId)
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return activityTagRelRepository.selectList(new LambdaQueryWrapper<ActivityTagRel>()
                        .in(ActivityTagRel::getTagId, tagIds))
                .stream()
                .map(ActivityTagRel::getActivityId)
                .distinct()
                .toList();
    }

    @Override
    public ActivityResponse getActivityDetail(Long activityId) {
        ActivityResponse cached = activityCacheService.get(activityId);
        if (cached != null) {
            cached.setDisplayStatus(ActivityStatusEnum.resolveDisplayStatus(
                    cached.getActivityStatus(), cached.getEndTime()));
            return cached;
        }
        if (activityCacheService.isKnownMissing(activityId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }

        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            activityCacheService.putMissing(activityId);
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }

        if (!ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())
                && !canViewUnreviewed(activityMain)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }

        ActivityResponse response = activityAssembler.toResponseWithTiers(activityMain);
        if (ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())) {
            activityCacheService.put(response);
        }
        return response;
    }

    @Override
    public List<ActivityTagVO> listActivityTags() {
        return activityTagRepository.selectList(new LambdaQueryWrapper<ActivityTag>()
                        .orderByAsc(ActivityTag::getTagId))
                .stream()
                .map(tag -> ActivityTagVO.builder()
                        .tagId(tag.getTagId())
                        .tagName(tag.getTagName())
                        .build())
                .toList();
    }

    @Override
    public List<ActivityResponse> listHotActivities(int limit) {
        List<Long> activityIds = hotActivityService.listHotActivityIds(limit);
        if (activityIds.isEmpty()) {
            return List.of();
        }
        List<ActivityMain> activities = activityMainRepository.selectBatchIds(activityIds);
        if (activities.isEmpty()) {
            return List.of();
        }
        Map<Long, ActivityMain> byId = activities.stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, Function.identity()));
        List<ActivityResponse> result = new ArrayList<>();
        for (Long activityId : activityIds) {
            ActivityMain activityMain = byId.get(activityId);
            if (activityMain == null
                    || !ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())) {
                hotActivityService.remove(activityId);
                continue;
            }
            result.add(activityAssembler.toResponse(activityMain));
        }
        return result;
    }

    @Override
    public ActivityStockResponse getActivityStock(Long activityId) {
        ActivityMain activityMain = requireActivity(activityId);
        if (!ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())
                && !canViewUnreviewed(activityMain)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        Integer maxParticipant = activityMain.getMaxParticipant();
        int current = activityMain.getCurrentParticipant() == null ? 0 : activityMain.getCurrentParticipant();
        boolean limited = maxParticipant != null && maxParticipant > 0;
        return ActivityStockResponse.builder()
                .activityId(activityId)
                .maxParticipant(maxParticipant)
                .currentParticipant(current)
                .remaining(limited ? Math.max(0, maxParticipant - current) : null)
                .limited(limited)
                .build();
    }

    private boolean canViewUnreviewed(ActivityMain activityMain) {
        String currentUserId = currentUser.userId().orElse(null);
        if (currentUserId == null) {
            return false;
        }
        if (currentUserId.equals(activityMain.getPublisherUserId())
                || currentUser.hasRole(UserRoleEnum.ADMIN.getCode())) {
            return true;
        }
        return activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, currentUserId)
                .eq(ActivityEnroll::getActivityId, activityMain.getActivityId())) > 0;
    }

    private ActivityMain requireActivity(Long activityId) {
        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activityMain;
    }
}
