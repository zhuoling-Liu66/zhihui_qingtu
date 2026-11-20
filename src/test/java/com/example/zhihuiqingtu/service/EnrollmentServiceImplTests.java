package com.example.zhihuiqingtu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.enrollment.dto.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.UpdateAttendanceRequest;
import com.example.zhihuiqingtu.award.dto.UpdateAwardRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityCreditTier;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.enrollment.enums.EnrollmentStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.support.ActivityStockService;
import com.example.zhihuiqingtu.enrollment.service.impl.EnrollmentServiceImpl;
import com.example.zhihuiqingtu.activity.support.HotActivityService;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantVO;
import com.example.zhihuiqingtu.enrollment.vo.EnrollmentResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.award.service.impl.ParticipantAwardServiceImpl;
import com.example.zhihuiqingtu.award.support.AwardRecordFactory;

/**
 * 报名、取消、签到/签退、考勤修正与发放管理的状态流转与权限定向测试。
 * <p>
 * 数据库层的"只成功一次"由条件 UPDATE 保证；这里用 Mockito 模拟影响行数，
 * 验证服务层对 0 行的处理、发放记录的生成规则，以及"发布者或管理员"权限边界。
 */
class EnrollmentServiceImplTests {

    private static final Long ACTIVITY_ID = 100L;
    private static final Long ENROLL_ID = 200L;
    private static final String USER_ID = "S0001";
    private static final String PUBLISHER_ID = "P0001";

    private final ActivityEnrollRepository activityEnrollRepository = mock(ActivityEnrollRepository.class);
    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final ActivityCreditTierRepository activityCreditTierRepository = mock(ActivityCreditTierRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final CreditRecordRepository creditRecordRepository = mock(CreditRecordRepository.class);
    private final ActivityStockService activityStockService = mock(ActivityStockService.class);
    private final ActivityCacheService activityCacheService = mock(ActivityCacheService.class);
    private final HotActivityService hotActivityService = mock(HotActivityService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final AwardRecordFactory awardRecordFactory = new AwardRecordFactory(creditRecordRepository);
    private final ParticipantAwardServiceImpl participantAwardService = new ParticipantAwardServiceImpl(
            activityEnrollRepository,
            activityMainRepository,
            activityCreditTierRepository,
            userInfoRepository,
            creditRecordRepository,
            currentUser,
            awardRecordFactory
    );
    private final EnrollmentServiceImpl service = new EnrollmentServiceImpl(
            activityEnrollRepository,
            activityMainRepository,
            userInfoRepository,
            creditRecordRepository,
            participantAwardService,
            awardRecordFactory,
            activityStockService,
            activityCacheService,
            hotActivityService,
            currentUser
    );

    @BeforeAll
    static void initMybatisPlusMetadata() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CreditRecord.class);
        TableInfoHelper.initTableInfo(assistant, ActivityMain.class);
        TableInfoHelper.initTableInfo(assistant, ActivityEnroll.class);
        TableInfoHelper.initTableInfo(assistant, ActivityCreditTier.class);
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @Test
    void capacityOneSecondEnrollmentIsRejected() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        when(userInfoRepository.selectById(USER_ID)).thenReturn(student());
        when(activityEnrollRepository.selectCount(any())).thenReturn(0L);
        when(activityStockService.tryDeduct(ACTIVITY_ID)).thenReturn(true);
        // 模拟容量 1 时，另一个并发请求已经占走名额：条件 UPDATE 影响 0 行
        when(activityMainRepository.tryOccupySeat(ACTIVITY_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createEnrollment(createRequest()));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        // 名额没占到时不能插入报名记录，也不能提前更新缓存/热榜
        verify(activityEnrollRepository, never()).insert(any(ActivityEnroll.class));
        verify(activityCacheService, never()).evict(any());
        verify(hotActivityService, never()).incrementEnroll(any());
    }

    @Test
    void successfulEnrollmentUpdatesCacheAndHotRank() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        when(userInfoRepository.selectById(USER_ID)).thenReturn(student());
        when(activityEnrollRepository.selectCount(any())).thenReturn(0L);
        when(activityStockService.tryDeduct(ACTIVITY_ID)).thenReturn(true);
        when(activityMainRepository.tryOccupySeat(ACTIVITY_ID)).thenReturn(1);
        when(activityEnrollRepository.insert(any(ActivityEnroll.class))).thenReturn(1);

        EnrollmentResponse response = service.createEnrollment(createRequest());

        assertEquals(ACTIVITY_ID, response.getActivityId());
        // 没有活动事务时 afterCommit 立即执行，验证缓存失效 + 热门榜加分确实发生
        verify(activityCacheService).evict(ACTIVITY_ID);
        verify(hotActivityService).incrementEnroll(ACTIVITY_ID);
    }

    @Test
    void duplicateCancelOnlyReleasesOnce() {
        stubSecondClassActivity(false);
        stubEnrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        // 模拟第二次取消：条件 UPDATE 影响 0 行
        when(activityEnrollRepository.cancelIfEnrolled(ENROLL_ID, USER_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.cancelEnrollment(ENROLL_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        // 没有真正取消成功就不能释放名额，否则名额会被多退
        verify(activityMainRepository, never()).releaseSeat(any());
        verify(hotActivityService, never()).decrementEnroll(any());
    }

    @Test
    void successfulCancelReleasesSeatAndDecrementsRank() {
        stubSecondClassActivity(false);
        stubEnrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        when(activityEnrollRepository.cancelIfEnrolled(ENROLL_ID, USER_ID)).thenReturn(1);

        EnrollmentResponse response = service.cancelEnrollment(ENROLL_ID);

        assertEquals(ENROLL_ID, response.getEnrollId());
        verify(activityMainRepository).releaseSeat(ACTIVITY_ID);
        verify(activityCacheService).evict(ACTIVITY_ID);
        verify(hotActivityService).decrementEnroll(ACTIVITY_ID);
    }

    @Test
    void duplicateSignInIsRejectedWithoutGeneratingAward() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        // 模拟第二个并发签到：条件 UPDATE 影响 0 行
        when(activityEnrollRepository.signInIfEnrolled(eq(ENROLL_ID), eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.signIn(ENROLL_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(creditRecordRepository, never()).insert(any(CreditRecord.class));
    }

    @Test
    void successfulSignInOnlyMarksSignedIn() {
        stubSecondClassActivity(true);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        // 第一次读取是签到前的"已报名"，签到成功后重新读取是"已签到"
        ActivityEnroll enrolled = enrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null, null);
        ActivityEnroll signedIn = enrollment(EnrollmentStatusEnum.SIGNED_IN.getCode(), LocalDateTime.now(), null);
        when(activityEnrollRepository.selectById(ENROLL_ID)).thenReturn(enrolled, signedIn);
        when(activityEnrollRepository.signInIfEnrolled(eq(ENROLL_ID), eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(1);

        EnrollmentResponse response = service.signIn(ENROLL_ID);

        assertEquals(EnrollmentStatusEnum.SIGNED_IN.getCode(), response.getEnrollStatus());
        assertNotNull(response.getSignInTime());
        // 签到不再生成发放记录，必须签退后才有
        verify(creditRecordRepository, never()).insert(any(CreditRecord.class));
    }

    @Test
    void signOutRequiresSignInFirst() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.ENROLLED.getCode(), null);
        when(currentUser.requireUserId()).thenReturn(USER_ID);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.signOut(ENROLL_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(activityEnrollRepository, never()).signOutIfSignedIn(any(), any(), any());
    }

    @Test
    void successfulSignOutGeneratesPendingCreditAward() {
        stubSecondClassActivity(true);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        ActivityEnroll signedIn = enrollment(EnrollmentStatusEnum.SIGNED_IN.getCode(), LocalDateTime.now(), null);
        ActivityEnroll completed = enrollment(EnrollmentStatusEnum.COMPLETED.getCode(),
                LocalDateTime.now(), LocalDateTime.now());
        when(activityEnrollRepository.selectById(ENROLL_ID)).thenReturn(signedIn, completed);
        when(activityEnrollRepository.signOutIfSignedIn(eq(ENROLL_ID), eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(1);
        when(creditRecordRepository.selectCount(any())).thenReturn(0L);

        EnrollmentResponse response = service.signOut(ENROLL_ID);

        assertEquals(EnrollmentStatusEnum.COMPLETED.getCode(), response.getEnrollStatus());
        assertNotNull(response.getSignOutTime());
        ArgumentCaptor<CreditRecord> captor = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecordRepository).insert(captor.capture());
        CreditRecord award = captor.getValue();
        assertEquals(USER_ID, award.getUserId());
        assertEquals(ACTIVITY_ID, award.getActivityId());
        assertEquals(new BigDecimal("1.00"), award.getCredit());
        assertEquals(2, award.getCreditType());
        assertNull(award.getVolunteerHours());
        assertEquals(ReviewStatusEnum.PENDING.getCode(), award.getVerifyStatus());
    }

    @Test
    void volunteerSignOutGeneratesHoursAward() {
        stubVolunteerActivity();
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        ActivityEnroll signedIn = enrollment(EnrollmentStatusEnum.SIGNED_IN.getCode(), LocalDateTime.now(), null);
        ActivityEnroll completed = enrollment(EnrollmentStatusEnum.COMPLETED.getCode(),
                LocalDateTime.now(), LocalDateTime.now());
        when(activityEnrollRepository.selectById(ENROLL_ID)).thenReturn(signedIn, completed);
        when(activityEnrollRepository.signOutIfSignedIn(eq(ENROLL_ID), eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(1);
        when(creditRecordRepository.selectCount(any())).thenReturn(0L);

        service.signOut(ENROLL_ID);

        ArgumentCaptor<CreditRecord> captor = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecordRepository).insert(captor.capture());
        CreditRecord award = captor.getValue();
        assertEquals(BigDecimal.ZERO, award.getCredit());
        assertNull(award.getCreditType());
        assertEquals(new BigDecimal("2.50"), award.getVolunteerHours());
        assertEquals(ReviewStatusEnum.PENDING.getCode(), award.getVerifyStatus());
    }

    @Test
    void publisherCanEditAwardAndConfirm() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(userInfoRepository.selectById(PUBLISHER_ID)).thenReturn(publisher());
        CreditRecord pendingAward = CreditRecord.builder()
                .recordId(300L)
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .credit(new BigDecimal("1.00"))
                .creditType(2)
                .verifyStatus(ReviewStatusEnum.PENDING.getCode())
                .build();
        when(creditRecordRepository.selectOne(any())).thenReturn(pendingAward);

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setCredit(new BigDecimal("1.50"));
        request.setCreditType(3);
        request.setVerifyStatus(ReviewStatusEnum.APPROVED.getCode());

        ActivityParticipantVO response = service.updateAward(ENROLL_ID, request);

        assertEquals(new BigDecimal("1.50"), response.getCredit());
        assertEquals(3, response.getCreditType());
        assertEquals(ReviewStatusEnum.APPROVED.getCode(), response.getVerifyStatus());
        assertNotNull(response.getVerifyTime());
        verify(creditRecordRepository).updateById(any(CreditRecord.class));
    }

    @Test
    void nonPublisherCannotCorrectAttendance() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.SIGNED_IN.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn("S9999");
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);

        UpdateAttendanceRequest request = new UpdateAttendanceRequest();
        request.setAction("MARK_SIGN_OUT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAttendance(ENROLL_ID, request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(activityEnrollRepository, never()).markSignOut(any(), any());
    }

    @Test
    void revertSignOutBlockedWhenAwardAlreadyApproved() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(creditRecordRepository.selectOne(any())).thenReturn(CreditRecord.builder()
                .recordId(300L)
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .verifyStatus(ReviewStatusEnum.APPROVED.getCode())
                .build());

        UpdateAttendanceRequest request = new UpdateAttendanceRequest();
        request.setAction("REVERT_SIGN_OUT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAttendance(ENROLL_ID, request));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(creditRecordRepository, never()).deleteUnconfirmedAward(any());
        verify(activityEnrollRepository, never()).revertSignOut(any());
    }

    @Test
    void publisherCanConfirmAllPendingAwards() {
        stubSecondClassActivity(true);
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(creditRecordRepository.update(any(), any())).thenReturn(2);

        int confirmed = service.confirmAwards(ACTIVITY_ID);

        assertEquals(2, confirmed);
        verify(creditRecordRepository).update(any(), any());
    }

    @Test
    void comboCreditAssignsTierAndChecksQuota() {
        stubComboCreditActivity();
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(userInfoRepository.selectById(PUBLISHER_ID)).thenReturn(publisher());
        CreditRecord award = CreditRecord.builder()
                .recordId(300L).userId(USER_ID).activityId(ACTIVITY_ID)
                .credit(BigDecimal.ZERO).creditType(2)
                .verifyStatus(0).build();
        when(creditRecordRepository.selectOne(any())).thenReturn(award);
        when(activityCreditTierRepository.selectList(any()))
                .thenReturn(List.of(tier("1.00", 3)));
        // 其他参与者已占用 1 个名额
        when(creditRecordRepository.selectList(any())).thenReturn(List.of(
                CreditRecord.builder().recordId(301L).userId("OTHER").activityId(ACTIVITY_ID)
                        .credit(new BigDecimal("1.00")).verifyStatus(1).build()));

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setCredit(new BigDecimal("1.00"));
        request.setCreditType(2);
        request.setVerifyStatus(1);

        ActivityParticipantVO response = service.updateAward(ENROLL_ID, request);

        assertEquals(new BigDecimal("1.00"), response.getCredit());
        assertEquals(1, response.getVerifyStatus());
        verify(creditRecordRepository).updateById(any(CreditRecord.class));
    }

    @Test
    void comboCreditRejectsFullTier() {
        stubComboCreditActivity();
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(creditRecordRepository.selectOne(any())).thenReturn(CreditRecord.builder()
                .recordId(300L).userId(USER_ID).activityId(ACTIVITY_ID)
                .credit(BigDecimal.ZERO).creditType(2).verifyStatus(0).build());
        when(activityCreditTierRepository.selectList(any()))
                .thenReturn(List.of(tier("1.00", 2)));
        // 名额 2 已被其他两人占满
        when(creditRecordRepository.selectList(any())).thenReturn(List.of(
                CreditRecord.builder().recordId(301L).userId("A").credit(new BigDecimal("1.00")).verifyStatus(1).build(),
                CreditRecord.builder().recordId(302L).userId("B").credit(new BigDecimal("1.00")).verifyStatus(3).build()));

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setCredit(new BigDecimal("1.00"));
        request.setVerifyStatus(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAward(ENROLL_ID, request));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(creditRecordRepository, never()).updateById(any(CreditRecord.class));
    }

    @Test
    void flexibleVolunteerPublisherSubmitsForAdminReview() {
        stubFlexibleVolunteerActivity();
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(userInfoRepository.selectById(PUBLISHER_ID)).thenReturn(publisher());
        when(creditRecordRepository.selectOne(any())).thenReturn(CreditRecord.builder()
                .recordId(300L).userId(USER_ID).activityId(ACTIVITY_ID)
                .credit(BigDecimal.ZERO).volunteerHours(null).verifyStatus(0).build());

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setVolunteerHours(new BigDecimal("3.50"));

        ActivityParticipantVO response = service.updateAward(ENROLL_ID, request);

        assertEquals(3, response.getVerifyStatus());
        assertEquals(new BigDecimal("3.50"), response.getVolunteerHours());
        assertNotNull(response.getVerifyTime());
    }

    @Test
    void flexibleVolunteerPublisherCannotApproveDirectly() {
        stubFlexibleVolunteerActivity();
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(creditRecordRepository.selectOne(any())).thenReturn(CreditRecord.builder()
                .recordId(300L).userId(USER_ID).activityId(ACTIVITY_ID)
                .credit(BigDecimal.ZERO).volunteerHours(new BigDecimal("3.00")).verifyStatus(3).build());

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setVerifyStatus(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAward(ENROLL_ID, request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void adminApprovesFlexibleVolunteerHours() {
        stubFlexibleVolunteerActivity();
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn("A0001");
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(true);
        when(userInfoRepository.selectById("A0001")).thenReturn(UserInfo.builder()
                .studentNo("A0001").userName("管理员").build());
        when(creditRecordRepository.selectOne(any())).thenReturn(CreditRecord.builder()
                .recordId(300L).userId(USER_ID).activityId(ACTIVITY_ID)
                .credit(BigDecimal.ZERO).volunteerHours(new BigDecimal("3.00")).verifyStatus(3).build());

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setVerifyStatus(1);

        ActivityParticipantVO response = service.updateAward(ENROLL_ID, request);

        assertEquals(1, response.getVerifyStatus());
        assertEquals(new BigDecimal("3.00"), response.getVolunteerHours());
    }

    @Test
    void adminBatchApprovesPendingReviewAwards() {
        stubSecondClassActivity(true);
        when(currentUser.requireUserId()).thenReturn("A0001");
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(true);
        // countAwards(status=3) 返回 2，随后批量更新也返回 2
        when(creditRecordRepository.selectCount(any())).thenReturn(2L);
        when(creditRecordRepository.update(any(), any())).thenReturn(2);

        int confirmed = service.confirmAwards(ACTIVITY_ID);

        assertEquals(2, confirmed);
        verify(creditRecordRepository).update(any(), any());
    }

    @Test
    void staffCannotApprovePendingReviewCredit() {
        stubSecondClassActivity(true);
        stubEnrollment(EnrollmentStatusEnum.COMPLETED.getCode(), LocalDateTime.now());
        when(currentUser.requireUserId()).thenReturn(PUBLISHER_ID);
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(creditRecordRepository.selectOne(any())).thenReturn(CreditRecord.builder()
                .recordId(300L).userId(USER_ID).activityId(ACTIVITY_ID)
                .credit(new BigDecimal("1.00")).creditType(2)
                .verifyStatus(3).build());

        UpdateAwardRequest request = new UpdateAwardRequest();
        request.setCredit(new BigDecimal("1.00"));
        request.setVerifyStatus(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAward(ENROLL_ID, request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    private void stubComboCreditActivity() {
        ActivityMain activity = ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(PUBLISHER_ID)
                .activityTitle("组合学分活动")
                .activityType(ActivityTypeEnum.SECOND_CLASS.getCode())
                .activityCredit(new BigDecimal("1.00"))
                .creditType(2)
                .creditMode(2)
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .reviewStatus(ReviewStatusEnum.APPROVED.getCode())
                .maxParticipant(10)
                .currentParticipant(0)
                .signInEnabled(1)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(activity);
    }

    private void stubFlexibleVolunteerActivity() {
        ActivityMain activity = ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(PUBLISHER_ID)
                .activityTitle("灵活志愿活动")
                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                .volunteerMode(2)
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .reviewStatus(ReviewStatusEnum.APPROVED.getCode())
                .maxParticipant(10)
                .currentParticipant(0)
                .signInEnabled(1)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(activity);
    }

    private ActivityCreditTier tier(String credit, int quota) {
        return ActivityCreditTier.builder()
                .credit(new BigDecimal(credit))
                .quota(quota)
                .build();
    }

    private void stubSecondClassActivity(boolean signInEnabled) {
        ActivityMain activity = ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(PUBLISHER_ID)
                .activityTitle("测试活动")
                .activityType(ActivityTypeEnum.SECOND_CLASS.getCode())
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .reviewStatus(ReviewStatusEnum.APPROVED.getCode())
                .maxParticipant(3)
                .currentParticipant(0)
                .signInEnabled(signInEnabled ? 1 : 0)
                .activityCredit(new BigDecimal("1.00"))
                .creditType(2)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(activityStockService.isLimited(any())).thenReturn(false);
    }

    private void stubVolunteerActivity() {
        ActivityMain activity = ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(PUBLISHER_ID)
                .activityTitle("志愿活动")
                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .reviewStatus(ReviewStatusEnum.APPROVED.getCode())
                .maxParticipant(3)
                .currentParticipant(0)
                .signInEnabled(1)
                .volunteerHours(new BigDecimal("2.50"))
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(activity);
    }

    private void stubEnrollment(Integer status, LocalDateTime signInTime) {
        when(activityEnrollRepository.selectById(ENROLL_ID))
                .thenReturn(enrollment(status, signInTime, null));
    }

    private ActivityEnroll enrollment(Integer status, LocalDateTime signInTime, LocalDateTime signOutTime) {
        return ActivityEnroll.builder()
                .enrollId(ENROLL_ID)
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .enrollStatus(status)
                .signInTime(signInTime)
                .signOutTime(signOutTime)
                .build();
    }

    private UserInfo student() {
        return UserInfo.builder().studentNo(USER_ID).userName("学生").build();
    }

    private UserInfo publisher() {
        return UserInfo.builder().studentNo(PUBLISHER_ID).userName("发布者").build();
    }

    private CreateEnrollmentRequest createRequest() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest();
        request.setActivityId(ACTIVITY_ID);
        return request;
    }
}
