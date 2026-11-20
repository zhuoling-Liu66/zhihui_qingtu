package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.config.UploadProperties;
import com.example.zhihuiqingtu.activity.dto.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.activity.dto.UpdateActivityRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.activity.service.impl.ActivityQueryServiceImpl;
import com.example.zhihuiqingtu.activity.support.ActivityAssembler;

/**
 * 活动编辑/状态变更的字段级更新测试。
 * <p>
 * 重点验证两条并发约束：
 * 1. 编辑活动只更新可编辑字段，不再整行写回（否则旧 currentParticipant 会覆盖并发报名结果）；
 * 2. 容量校验与实际更新在同一条条件 UPDATE 中，影响 0 行时返回冲突。
 */
class ActivityServiceImplTests {

    private static final Long ACTIVITY_ID = 300L;
    private static final String USER_ID = "P0001";

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
    void updateActivityDoesNotOverwriteParticipantCount() {
        stubActivity(5);
        when(activityMainRepository.updateEditableFields(any(ActivityMain.class))).thenReturn(1);

        ActivityResponse response = service.updateActivity(ACTIVITY_ID, updateRequest(10));

        assertEquals(ACTIVITY_ID, response.getActivityId());
        verify(activityMainRepository).updateEditableFields(any(ActivityMain.class));
        // 关键断言：不再使用整行 updateById，避免把旧 currentParticipant 写回
        verify(activityMainRepository, never()).updateById(any(ActivityMain.class));
        verify(activityCacheService).evict(ACTIVITY_ID);
    }

    @Test
    void updateActivityRejectsWhenCapacityConditionFails() {
        stubActivity(5);
        // 模拟校验之后又有并发报名，容量条件在同一条 UPDATE 里重新求值失败（影响 0 行）
        when(activityMainRepository.updateEditableFields(any(ActivityMain.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateActivity(ACTIVITY_ID, updateRequest(10)));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        // 更新没成功就不能重置库存/失效缓存
        verify(activityCacheService, never()).evict(any());
        verify(activityStockService, never()).reset(any(), any(), any());
    }

    @Test
    void changeStatusOnlyUpdatesStatusColumn() {
        stubActivity(3);
        ActivityMain updated = ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(USER_ID)
                .activityTitle("原标题")
                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                .activityStatus(ActivityStatusEnum.CANCELED.getCode())
                .maxParticipant(3)
                .currentParticipant(3)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();
        // 第一次读取是更新前，更新后重新读取到已取消状态
        when(activityMainRepository.selectById(ACTIVITY_ID))
                .thenReturn(ActivityMain.builder()
                                .activityId(ACTIVITY_ID)
                                .publisherUserId(USER_ID)
                                .activityTitle("原标题")
                                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                                .maxParticipant(3)
                                .currentParticipant(3)
                                .startTime(LocalDateTime.now().minusDays(1))
                                .endTime(LocalDateTime.now().plusDays(1))
                                .build(),
                        updated);
        when(activityMainRepository.updateStatus(ACTIVITY_ID, ActivityStatusEnum.CANCELED.getCode())).thenReturn(1);

        ChangeActivityStatusRequest request = new ChangeActivityStatusRequest();
        request.setActivityStatus(ActivityStatusEnum.CANCELED.getCode());
        ActivityResponse response = service.changeActivityStatus(ACTIVITY_ID, request);

        assertEquals(ActivityStatusEnum.CANCELED.getCode(), response.getActivityStatus());
        // 只更新单列，不再整行写回
        verify(activityMainRepository).updateStatus(ACTIVITY_ID, ActivityStatusEnum.CANCELED.getCode());
        verify(activityMainRepository, never()).updateById(any(ActivityMain.class));
        verify(activityCacheService).evict(ACTIVITY_ID);
    }

    private void stubActivity(int currentParticipant) {
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        when(userInfoRepository.selectById(USER_ID)).thenReturn(UserInfo.builder()
                .studentNo(USER_ID)
                .userRole(UserRoleEnum.ADMIN.getCode())
                .build());
        ActivityMain activity = ActivityMain.builder()
                .activityId(ACTIVITY_ID)
                .publisherUserId(USER_ID)
                .activityTitle("原标题")
                .activityType(ActivityTypeEnum.VOLUNTEER.getCode())
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .maxParticipant(3)
                .currentParticipant(currentParticipant)
                .signInEnabled(0)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build();
        when(activityMainRepository.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(activityTagRelRepository.selectList(any())).thenReturn(java.util.Collections.emptyList());
    }

    private UpdateActivityRequest updateRequest(int maxParticipant) {
        UpdateActivityRequest request = new UpdateActivityRequest();
        request.setActivityTitle("新标题");
        // 客户端只能编辑运动/兴趣类活动（学分与志愿活动走管理端）
        request.setActivityType(ActivityTypeEnum.SPORTS.getCode());
        request.setStartTime(LocalDateTime.now().minusHours(2));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        request.setMaxParticipant(maxParticipant);
        return request;
    }
}
