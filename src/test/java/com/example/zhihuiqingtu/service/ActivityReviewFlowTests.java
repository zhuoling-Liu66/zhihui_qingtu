package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.config.UploadProperties;
import com.example.zhihuiqingtu.activity.dto.CreateActivityRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.team.repository.TeamMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.service.impl.ActivityServiceImpl;
import com.example.zhihuiqingtu.activity.support.ActivityStockService;
import com.example.zhihuiqingtu.activity.support.HotActivityService;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.activity.dto.CreditTierRequest;
import com.example.zhihuiqingtu.activity.service.impl.ActivityQueryServiceImpl;
import com.example.zhihuiqingtu.activity.support.ActivityAssembler;

/**
 * 新发布流程的定向测试：
 * 1. 学生/老师发布无学分活动 → 待审核；
 * 2. 学分活动只能由 STAFF 创建；
 * 3. 未审核活动对非发布者不可见。
 */
class ActivityReviewFlowTests {

    private static final String USER_ID = "S0001";
    private static final Long ACTIVITY_ID = 500L;

    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final ActivityCreditTierRepository activityCreditTierRepository = mock(ActivityCreditTierRepository.class);
    private final ActivityEnrollRepository activityEnrollRepository = mock(ActivityEnrollRepository.class);
    private final ActivityTagRepository activityTagRepository = mock(ActivityTagRepository.class);
    private final ActivityTagRelRepository activityTagRelRepository = mock(ActivityTagRelRepository.class);
    private final CreditRecordRepository creditRecordRepository = mock(CreditRecordRepository.class);
    private final TeamMainRepository teamMainRepository = mock(TeamMainRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final UploadProperties uploadProperties = mock(UploadProperties.class);
    private final ActivityCacheService activityCacheService = mock(ActivityCacheService.class);
    private final ActivityStockService activityStockService = mock(ActivityStockService.class);
    private final HotActivityService hotActivityService = mock(HotActivityService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final ActivityAssembler activityAssembler = new ActivityAssembler(
            activityTagRelRepository,
            activityTagRepository,
            activityCreditTierRepository
    );
    private final ActivityQueryServiceImpl activityQueryService = new ActivityQueryServiceImpl(
            activityMainRepository,
            activityTagRelRepository,
            activityTagRepository,
            activityEnrollRepository,
            activityCacheService,
            hotActivityService,
            activityAssembler,
            currentUser
    );
    private final ActivityServiceImpl service = new ActivityServiceImpl(
            activityQueryService,
            activityAssembler,
            activityMainRepository,
            activityCreditTierRepository,
            activityEnrollRepository,
            activityTagRepository,
            activityTagRelRepository,
            creditRecordRepository,
            teamMainRepository,
            teamMemberRepository,
            userInfoRepository,
            uploadProperties,
            activityCacheService,
            activityStockService,
            hotActivityService,
            currentUser
    );

    @Test
    void studentCreatedActivityStartsPendingReview() {
        stubUser(UserRoleEnum.STUDENT.getCode(), USER_ID);
        stubInsertAndTags();

        ActivityResponse response = service.createActivity(sportsActivityRequest());

        assertEquals(ReviewStatusEnum.PENDING.getCode(), response.getReviewStatus());
        assertEquals(BigDecimal.ZERO, response.getActivityCredit());
        ArgumentCaptor<ActivityMain> captor = ArgumentCaptor.forClass(ActivityMain.class);
        verify(activityMainRepository).insert(captor.capture());
        assertEquals(ReviewStatusEnum.PENDING.getCode(), captor.getValue().getReviewStatus());
    }

    @Test
    void teacherCanPublishNormalActivityWithoutPublisherApplication() {
        stubUser(UserRoleEnum.TEACHER.getCode(), "T0001");
        stubInsertAndTags();

        ActivityResponse response = service.createActivity(sportsActivityRequest());

        assertEquals(ReviewStatusEnum.PENDING.getCode(), response.getReviewStatus());
    }

    @Test
    void studentCannotCreateVolunteerActivity() {
        stubUser(UserRoleEnum.STUDENT.getCode(), USER_ID);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createActivity(normalActivityRequest()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        assertTrue(exception.getMessage().contains("志愿"));
    }

    @Test
    void staffCanPublishFlexibleVolunteerActivity() {
        stubUser(UserRoleEnum.STAFF.getCode(), "O0001");
        stubInsertAndTags();

        CreateActivityRequest request = normalActivityRequest();
        request.setVolunteerHours(null);
        request.setVolunteerMode(2);

        ActivityResponse response = service.createStaffActivity(request);

        assertEquals(ReviewStatusEnum.PENDING.getCode(), response.getReviewStatus());
        assertEquals(2, response.getVolunteerMode());
    }

    @Test
    void staffCanPublishComboCreditActivity() {
        stubUser(UserRoleEnum.STAFF.getCode(), "O0001");
        stubInsertAndTags();

        CreateActivityRequest request = normalActivityRequest();
        request.setActivityType(ActivityTypeEnum.SECOND_CLASS.getCode());
        request.setActivityCredit(null);
        request.setCreditType(2);
        request.setCreditMode(2);
        request.setVolunteerHours(null);
        com.example.zhihuiqingtu.activity.dto.CreditTierRequest tier1 =
                new com.example.zhihuiqingtu.activity.dto.CreditTierRequest();
        tier1.setCredit(new BigDecimal("1.00"));
        tier1.setQuota(3);
        com.example.zhihuiqingtu.activity.dto.CreditTierRequest tier2 =
                new com.example.zhihuiqingtu.activity.dto.CreditTierRequest();
        tier2.setCredit(new BigDecimal("0.50"));
        tier2.setQuota(20);
        request.setCreditTiers(List.of(tier1, tier2));

        ActivityResponse response = service.createStaffActivity(request);

        assertEquals(ReviewStatusEnum.PENDING.getCode(), response.getReviewStatus());
        assertEquals(2, response.getCreditMode());
        // activity_credit 存最大档，用于列表展示
        assertEquals(new BigDecimal("1.00"), response.getActivityCredit());
    }

    @Test
    void studentCannotCreateCreditActivity() {
        stubUser(UserRoleEnum.STUDENT.getCode(), USER_ID);

        CreateActivityRequest request = normalActivityRequest();
        request.setActivityType(ActivityTypeEnum.SECOND_CLASS.getCode());
        request.setActivityCredit(new BigDecimal("1.00"));
        request.setCreditType(2);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createStaffActivity(request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void staffCreatedCreditActivityStartsPendingReview() {
        stubUser(UserRoleEnum.STAFF.getCode(), "O0001");
        stubInsertAndTags();

        CreateActivityRequest request = normalActivityRequest();
        request.setActivityType(ActivityTypeEnum.SECOND_CLASS.getCode());
        request.setActivityCredit(new BigDecimal("1.00"));
        request.setCreditType(2);
        request.setVolunteerHours(null);

        ActivityResponse response = service.createStaffActivity(request);

        assertEquals(ReviewStatusEnum.PENDING.getCode(), response.getReviewStatus());
        assertEquals(new BigDecimal("1.00"), response.getActivityCredit());
        assertNull(response.getVolunteerHours());
    }

    @Test
    void adminCreatedCreditActivityIsApprovedImmediately() {
        stubUser(UserRoleEnum.ADMIN.getCode(), "A0001");
        stubInsertAndTags();

        CreateActivityRequest request = normalActivityRequest();
        request.setActivityType(ActivityTypeEnum.SECOND_CLASS.getCode());
        request.setActivityCredit(new BigDecimal("1.00"));
        request.setCreditType(2);
        request.setVolunteerHours(null);

        ActivityResponse response = service.createAdminActivity(request);

        assertEquals(ReviewStatusEnum.APPROVED.getCode(), response.getReviewStatus());
        ArgumentCaptor<ActivityMain> captor = ArgumentCaptor.forClass(ActivityMain.class);
        verify(activityMainRepository).insert(captor.capture());
        assertEquals(ReviewStatusEnum.APPROVED.getCode(), captor.getValue().getReviewStatus());
        assertEquals("A0001", captor.getValue().getReviewerUserId());
        assertNotNull(captor.getValue().getReviewTime());
    }

    @Test
    void adminCanPublishVolunteerActivityDirectly() {
        stubUser(UserRoleEnum.ADMIN.getCode(), "A0001");
        stubInsertAndTags();

        ActivityResponse response = service.createAdminActivity(normalActivityRequest());

        assertEquals(ReviewStatusEnum.APPROVED.getCode(), response.getReviewStatus());
        assertEquals(new BigDecimal("2.0"), response.getVolunteerHours());
    }

    @Test
    void nonAdminCannotUseAdminPublishPath() {
        stubUser(UserRoleEnum.STUDENT.getCode(), USER_ID);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createAdminActivity(normalActivityRequest()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void pendingActivityDetailHiddenFromOtherUsers() {
        when(activityCacheService.get(ACTIVITY_ID)).thenReturn(null);
        when(activityCacheService.isKnownMissing(ACTIVITY_ID)).thenReturn(false);
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(pendingActivity("P0001"));
        when(currentUser.userId()).thenReturn(Optional.of(USER_ID));
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getActivityDetail(ACTIVITY_ID));

        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
    }

    @Test
    void pendingActivityDetailVisibleToPublisher() {
        when(activityCacheService.get(ACTIVITY_ID)).thenReturn(null);
        when(activityCacheService.isKnownMissing(ACTIVITY_ID)).thenReturn(false);
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(pendingActivity(USER_ID));
        when(currentUser.userId()).thenReturn(Optional.of(USER_ID));
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(activityTagRelRepository.selectList(any())).thenReturn(List.of());

        ActivityResponse response = service.getActivityDetail(ACTIVITY_ID);

        assertEquals(ReviewStatusEnum.PENDING.getCode(), response.getReviewStatus());
    }

    private void stubUser(Integer role, String userId) {
        when(currentUser.requireUserId()).thenReturn(userId);
        when(userInfoRepository.selectById(userId)).thenReturn(UserInfo.builder()
                .studentNo(userId)
                .userName("测试用户")
                .userRole(role)
                .build());
    }

    private void stubInsertAndTags() {
        doAnswer(invocation -> {
            ActivityMain activity = invocation.getArgument(0);
            activity.setActivityId(ACTIVITY_ID);
            return 1;
        }).when(activityMainRepository).insert(any(ActivityMain.class));
        when(activityTagRelRepository.selectList(any())).thenReturn(List.of());
    }

    private CreateActivityRequest sportsActivityRequest() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setActivityTitle("校园夜跑");
        request.setActivityDesc("测试活动");
        request.setActivityType(ActivityTypeEnum.SPORTS.getCode());
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(2));
        request.setActivityLocation("操场");
        request.setMaxParticipant(30);
        return request;
    }

    private CreateActivityRequest normalActivityRequest() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setActivityTitle("校园志愿活动");
        request.setActivityDesc("测试活动");
        request.setActivityType(ActivityTypeEnum.VOLUNTEER.getCode());
        request.setVolunteerHours(new BigDecimal("2.0"));
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(2));
        request.setActivityLocation("大学生活动中心");
        request.setMaxParticipant(30);
        return request;
    }

    private ActivityMain pendingActivity(String publisherId) {
        return ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(publisherId)
                .activityTitle("待审核活动")
                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .reviewStatus(ReviewStatusEnum.PENDING.getCode())
                .volunteerHours(new BigDecimal("2.0"))
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(2))
                .maxParticipant(30)
                .currentParticipant(0)
                .signInEnabled(0)
                .build();
    }
}
