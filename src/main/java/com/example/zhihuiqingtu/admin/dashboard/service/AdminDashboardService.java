package com.example.zhihuiqingtu.admin.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.dashboard.vo.AdminDashboardVO;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.example.zhihuiqingtu.admin.activity.service.AdminActivityService;
import com.example.zhihuiqingtu.admin.user.service.AdminUserService;

/**
 * 管理端概览统计服务。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int RECENT_LIMIT = 5;

    private final AdminGuard adminGuard;
    private final UserInfoRepository userInfoRepository;
    private final ActivityMainRepository activityMainRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final AdminUserService adminUserService;
    private final AdminActivityService adminActivityService;

    /**
     * 汇总用户、活动、待审核活动与待审核学分的总量，并附带最近数据。
     */
    public AdminDashboardVO overview() {
        adminGuard.requireAdmin();
        LocalDateTime now = LocalDateTime.now();

        return AdminDashboardVO.builder()
                .userTotal(userInfoRepository.selectCount(null))
                .studentCount(countUsersByRole(UserRoleEnum.STUDENT.getCode()))
                .teacherCount(countUsersByRole(UserRoleEnum.TEACHER.getCode()))
                .adminCount(countUsersByRole(UserRoleEnum.ADMIN.getCode()))
                .staffCount(countUsersByRole(UserRoleEnum.STAFF.getCode()))
                .activityTotal(activityMainRepository.selectCount(null))
                .pendingActivityCount(activityMainRepository.selectCount(new LambdaQueryWrapper<ActivityMain>()
                        .eq(ActivityMain::getReviewStatus, ReviewStatusEnum.PENDING.getCode())))
                .publishedActivityCount(activityMainRepository.selectCount(new LambdaQueryWrapper<ActivityMain>()
                        .eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                        .eq(ActivityMain::getReviewStatus, ReviewStatusEnum.APPROVED.getCode())
                        .gt(ActivityMain::getEndTime, now)))
                .finishedActivityCount(activityMainRepository.selectCount(new LambdaQueryWrapper<ActivityMain>()
                        .eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                        .le(ActivityMain::getEndTime, now)))
                .canceledActivityCount(activityMainRepository.selectCount(new LambdaQueryWrapper<ActivityMain>()
                        .eq(ActivityMain::getActivityStatus, ActivityStatusEnum.CANCELED.getCode())))
                .pendingCreditCount(creditRecordRepository.selectCount(new LambdaQueryWrapper<CreditRecord>()
                        .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.PENDING.getCode())))
                .recentActivities(adminActivityService.listRecent(RECENT_LIMIT))
                .recentUsers(adminUserService.listRecent(RECENT_LIMIT))
                .build();
    }

    private Long countUsersByRole(Integer roleCode) {
        return userInfoRepository.selectCount(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUserRole, roleCode));
    }
}
