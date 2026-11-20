package com.example.zhihuiqingtu.admin.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.activity.dto.AdminActivityQueryRequest;
import com.example.zhihuiqingtu.admin.activity.dto.AdminReviewActivityRequest;
import com.example.zhihuiqingtu.admin.support.AdminPages;
import com.example.zhihuiqingtu.admin.activity.vo.AdminActivityVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.common.TransactionEvents;
import com.example.zhihuiqingtu.activity.dto.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.activity.dto.CreateActivityRequest;
import com.example.zhihuiqingtu.activity.dto.UpdateActivityRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.activity.service.ActivityService;
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端活动服务。
 *
 * <p>管理员：发布全类型活动（免审核）、审核、下架、删除，并可管理任意活动的参与人员；
 * 运营人员：创建/编辑自己的学分活动（仍需管理员审核），管理自己活动的参与人员。
 */
@Service
@RequiredArgsConstructor
public class AdminActivityService {

    private final AdminGuard adminGuard;
    private final ActivityMainRepository activityMainRepository;
    private final UserInfoRepository userInfoRepository;
    private final ActivityService activityService;
    private final ActivityCacheService activityCacheService;

    /**
     * 管理员分页查询全部活动（含待审核/已驳回）。
     */
    public PageResponse<AdminActivityVO> queryActivities(AdminActivityQueryRequest request) {
        adminGuard.requireAdmin();
        return doQuery(request);
    }

    /**
     * 运营人员查询自己发布的活动（学分与志愿）。
     */
    public PageResponse<AdminActivityVO> queryMyActivities(AdminActivityQueryRequest request) {
        UserInfo staff = adminGuard.requireStaffOrAdmin();
        request.setPublisherUserId(staff.getStudentNo());
        return doQuery(request);
    }

    /**
     * 运营人员创建学分或志愿活动，创建后进入待审核。
     */
    public ActivityResponse createStaffActivity(CreateActivityRequest request) {
        adminGuard.requireStaff();
        return activityService.createStaffActivity(request);
    }

    /**
     * 管理员在管理端发布活动（全类型）。
     *
     * <p>管理员是审核人，因此管理员创建的活动直接视为已审核、立即公开；
     * 运营人员创建的活动仍走待审核队列。
     */
    public ActivityResponse createActivityByAdmin(CreateActivityRequest request) {
        adminGuard.requireAdmin();
        return activityService.createAdminActivity(request);
    }

    /**
     * 运营人员编辑自己的活动（学分或志愿）；编辑后重新进入待审核。
     */
    public ActivityResponse updateStaffActivity(Long activityId, UpdateActivityRequest request) {
        adminGuard.requireStaffOrAdmin();
        return activityService.updateStaffActivity(activityId, request);
    }

    /**
     * 管理员审核活动。
     *
     * @param activityId 活动主键
     * @param request    审核结论
     * @return 审核后的活动
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminActivityVO reviewActivity(Long activityId, AdminReviewActivityRequest request) {
        String reviewerUserId = adminGuard.requireAdmin();
        Integer reviewStatus = request.getReviewStatus();
        if (!ReviewStatusEnum.APPROVED.getCode().equals(reviewStatus)
                && !ReviewStatusEnum.REJECTED.getCode().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核结论仅支持通过或驳回");
        }
        if (ReviewStatusEnum.REJECTED.getCode().equals(reviewStatus)
                && !StringUtils.hasText(request.getReviewReason())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "驳回活动时必须填写原因");
        }

        ActivityMain activity = activityMainRepository.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        if (ReviewStatusEnum.APPROVED.getCode().equals(activity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动已经审核通过，无需重复审核");
        }

        int updated = activityMainRepository.reviewActivity(
                activityId,
                reviewStatus,
                request.getReviewReason(),
                reviewerUserId,
                LocalDateTime.now()
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动审核状态已变化，请刷新后重试");
        }
        TransactionEvents.afterCommit("活动审核后失效详情缓存 activityId=" + activityId,
                () -> activityCacheService.evict(activityId));

        AdminActivityVO vo = toVO(activityMainRepository.selectById(activityId),
                loadUserNames(List.of(activity.getPublisherUserId())).get(activity.getPublisherUserId()));
        return vo;
    }

    /**
     * 管理员下架或重新发布活动。
     */
    public ActivityResponse changeStatus(Long activityId, ChangeActivityStatusRequest request) {
        adminGuard.requireAdmin();
        if (ActivityStatusEnum.PUBLISHED.getCode().equals(request.getActivityStatus())) {
            ActivityMain activity = activityMainRepository.selectById(activityId);
            if (activity == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
            }
            if (!ReviewStatusEnum.APPROVED.getCode().equals(activity.getReviewStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "活动尚未通过审核，不能发布");
            }
        }
        return activityService.changeActivityStatus(activityId, request);
    }

    /**
     * 管理员删除活动（连同报名、学分、队伍等关联数据）。
     */
    public void deleteActivity(Long activityId) {
        adminGuard.requireAdmin();
        activityService.deleteActivity(activityId);
    }

    /**
     * 最近发布的活动（概览页用）。
     */
    public List<AdminActivityVO> listRecent(int limit) {
        Page<ActivityMain> page = activityMainRepository.selectPage(
                new Page<>(1, Math.max(limit, 1)),
                new LambdaQueryWrapper<ActivityMain>().orderByDesc(ActivityMain::getCreateTime)
        );
        return toVOs(page.getRecords());
    }

    private PageResponse<AdminActivityVO> doQuery(AdminActivityQueryRequest request) {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<ActivityMain> queryWrapper = new LambdaQueryWrapper<ActivityMain>()
                .eq(request.getActivityType() != null, ActivityMain::getActivityType, request.getActivityType())
                .eq(request.getReviewStatus() != null, ActivityMain::getReviewStatus, request.getReviewStatus())
                .eq(StringUtils.hasText(request.getPublisherUserId()),
                        ActivityMain::getPublisherUserId, request.getPublisherUserId());

        if (request.getActivityStatus() != null) {
            applyStatusFilter(queryWrapper, request.getActivityStatus(), now);
        }

        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            List<String> matchedPublisherIds = userInfoRepository.selectList(
                            new LambdaQueryWrapper<UserInfo>()
                                    .select(UserInfo::getStudentNo)
                                    .like(UserInfo::getUserName, keyword))
                    .stream()
                    .map(UserInfo::getStudentNo)
                    .toList();
            queryWrapper.and(wrapper -> {
                wrapper.like(ActivityMain::getActivityTitle, keyword)
                        .or().like(ActivityMain::getActivityDesc, keyword)
                        .or().like(ActivityMain::getActivityLocation, keyword);
                if (!matchedPublisherIds.isEmpty()) {
                    wrapper.or().in(ActivityMain::getPublisherUserId, matchedPublisherIds);
                }
            });
        }

        queryWrapper.orderByDesc(ActivityMain::getCreateTime);
        Page<ActivityMain> page = activityMainRepository.selectPage(
                AdminPages.of(request.getPageNo(), request.getPageSize()),
                queryWrapper
        );
        Page<AdminActivityVO> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(toVOs(page.getRecords()));
        return PageResponse.from(responsePage);
    }

    private void applyStatusFilter(LambdaQueryWrapper<ActivityMain> queryWrapper,
                                   Integer status,
                                   LocalDateTime now) {
        if (ActivityStatusEnum.PUBLISHED.getCode().equals(status)) {
            queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                    .gt(ActivityMain::getEndTime, now);
            return;
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(status)) {
            queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                    .le(ActivityMain::getEndTime, now);
            return;
        }
        if (ActivityStatusEnum.CANCELED.getCode().equals(status)) {
            queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.CANCELED.getCode());
            return;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "活动状态仅支持未结束、已结束或已取消");
    }

    private List<AdminActivityVO> toVOs(List<ActivityMain> activities) {
        Map<String, String> publisherNames = loadUserNames(
                activities.stream().map(ActivityMain::getPublisherUserId).collect(Collectors.toSet()));
        return activities.stream()
                .map(activity -> toVO(activity, publisherNames.get(activity.getPublisherUserId())))
                .toList();
    }

    private Map<String, String> loadUserNames(Collection<String> userIds) {
        Set<String> ids = userIds.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, UserInfo::getUserName, (a, b) -> a));
    }

    private AdminActivityVO toVO(ActivityMain activity, String publisherName) {
        if (activity == null) {
            return null;
        }
        return AdminActivityVO.builder()
                .activityId(activity.getActivityId())
                .activityTitle(activity.getActivityTitle())
                .activityDesc(activity.getActivityDesc())
                .publisherUserId(activity.getPublisherUserId())
                .publisherName(publisherName)
                .activityType(activity.getActivityType())
                .activityCredit(activity.getActivityCredit())
                .creditType(activity.getCreditType())
                .creditMode(activity.getCreditMode())
                .volunteerHours(activity.getVolunteerHours())
                .volunteerMode(activity.getVolunteerMode())
                .coverUrl(activity.getCoverUrl())
                .activityStatus(activity.getActivityStatus())
                .displayStatus(ActivityStatusEnum.resolveDisplayStatus(
                        activity.getActivityStatus(), activity.getEndTime()))
                .reviewStatus(activity.getReviewStatus())
                .reviewReason(activity.getReviewReason())
                .reviewerUserId(activity.getReviewerUserId())
                .reviewTime(activity.getReviewTime())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .activityLocation(activity.getActivityLocation())
                .maxParticipant(activity.getMaxParticipant())
                .currentParticipant(activity.getCurrentParticipant())
                .signInEnabled(activity.getSignInEnabled())
                .createTime(activity.getCreateTime())
                .build();
    }
}
