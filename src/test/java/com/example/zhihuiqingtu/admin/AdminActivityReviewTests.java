package com.example.zhihuiqingtu.admin;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.activity.dto.CreateActivityRequest;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.admin.activity.dto.AdminReviewActivityRequest;
import com.example.zhihuiqingtu.admin.activity.service.AdminActivityService;
import com.example.zhihuiqingtu.admin.activity.vo.AdminActivityVO;
import com.example.zhihuiqingtu.admin.support.AdminGuard;

/**
 * 管理员审核活动的定向测试。
 */
class AdminActivityReviewTests {

    private static final Long ACTIVITY_ID = 700L;
    private static final String ADMIN_ID = "A0001";
    private static final String PUBLISHER_ID = "S0001";

    private final AdminGuard adminGuard = mock(AdminGuard.class);
    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final ActivityCacheService activityCacheService = mock(ActivityCacheService.class);

    private final AdminActivityService service = new AdminActivityService(
            adminGuard,
            activityMainRepository,
            userInfoRepository,
            activityService,
            activityCacheService
    );

    @Test
    void adminCreateDelegatesToAutoApprovedPublishPath() {
        when(adminGuard.requireAdmin()).thenReturn(ADMIN_ID);
        CreateActivityRequest request = new CreateActivityRequest();
        request.setActivityTitle("管理员发布的活动");
        request.setActivityType(ActivityTypeEnum.VOLUNTEER.getCode());
        ActivityResponse expected = ActivityResponse.builder()
                .activityId(ACTIVITY_ID)
                .reviewStatus(ReviewStatusEnum.APPROVED.getCode())
                .build();
        when(activityService.createAdminActivity(request)).thenReturn(expected);

        ActivityResponse response = service.createActivityByAdmin(request);

        assertEquals(ReviewStatusEnum.APPROVED.getCode(), response.getReviewStatus());
        verify(activityService).createAdminActivity(request);
    }

    @Test
    void staffCannotUseAdminPublishPath() {
        when(adminGuard.requireAdmin()).thenThrow(new BusinessException(
                ErrorCode.FORBIDDEN, "仅管理员可以执行该操作"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createActivityByAdmin(new CreateActivityRequest()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void adminApprovesPendingActivity() {
        when(adminGuard.requireAdmin()).thenReturn(ADMIN_ID);
        ActivityMain pending = activity(ReviewStatusEnum.PENDING.getCode());
        ActivityMain approved = activity(ReviewStatusEnum.APPROVED.getCode());
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(pending, approved);
        when(activityMainRepository.reviewActivity(eq(ACTIVITY_ID), eq(ReviewStatusEnum.APPROVED.getCode()),
                any(), eq(ADMIN_ID), any(LocalDateTime.class))).thenReturn(1);
        when(userInfoRepository.selectBatchIds(any())).thenReturn(List.of(UserInfo.builder()
                .studentNo(PUBLISHER_ID)
                .userName("发布者")
                .build()));

        AdminReviewActivityRequest request = new AdminReviewActivityRequest();
        request.setReviewStatus(ReviewStatusEnum.APPROVED.getCode());

        AdminActivityVO response = service.reviewActivity(ACTIVITY_ID, request);

        assertEquals(ReviewStatusEnum.APPROVED.getCode(), response.getReviewStatus());
        assertEquals("发布者", response.getPublisherName());
        verify(activityCacheService).evict(ACTIVITY_ID);
    }

    @Test
    void rejectRequiresReason() {
        when(adminGuard.requireAdmin()).thenReturn(ADMIN_ID);
        AdminReviewActivityRequest request = new AdminReviewActivityRequest();
        request.setReviewStatus(ReviewStatusEnum.REJECTED.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviewActivity(ACTIVITY_ID, request));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    void alreadyApprovedActivityCannotBeReviewedAgain() {
        when(adminGuard.requireAdmin()).thenReturn(ADMIN_ID);
        when(activityMainRepository.selectById(ACTIVITY_ID))
                .thenReturn(activity(ReviewStatusEnum.APPROVED.getCode()));

        AdminReviewActivityRequest request = new AdminReviewActivityRequest();
        request.setReviewStatus(ReviewStatusEnum.REJECTED.getCode());
        request.setReviewReason("重复审核");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviewActivity(ACTIVITY_ID, request));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    private ActivityMain activity(Integer reviewStatus) {
        return ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(PUBLISHER_ID)
                .activityTitle("待审核活动")
                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .reviewStatus(reviewStatus)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(2))
                .maxParticipant(30)
                .currentParticipant(0)
                .signInEnabled(0)
                .build();
    }
}
