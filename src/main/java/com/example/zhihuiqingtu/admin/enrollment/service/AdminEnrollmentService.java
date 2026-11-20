package com.example.zhihuiqingtu.admin.enrollment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.enrollment.dto.AdminEnrollmentQueryRequest;
import com.example.zhihuiqingtu.admin.support.AdminPages;
import com.example.zhihuiqingtu.admin.enrollment.vo.AdminEnrollmentVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端报名查询服务（只读）。
 */
@Service
@RequiredArgsConstructor
public class AdminEnrollmentService {

    private final AdminGuard adminGuard;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityMainRepository activityMainRepository;
    private final UserInfoRepository userInfoRepository;

    /**
     * 分页查询全部报名记录，支持按活动、学号、报名状态和签到情况筛选。
     */
    public PageResponse<AdminEnrollmentVO> queryEnrollments(AdminEnrollmentQueryRequest request) {
        adminGuard.requireAdmin();
        Integer signedIn = request.getSignedIn();
        if (signedIn != null && signedIn != 0 && signedIn != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "签到状态仅支持 0 或 1");
        }

        LambdaQueryWrapper<ActivityEnroll> queryWrapper = new LambdaQueryWrapper<ActivityEnroll>()
                .eq(request.getActivityId() != null, ActivityEnroll::getActivityId, request.getActivityId())
                .like(StringUtils.hasText(request.getUserId()), ActivityEnroll::getUserId, request.getUserId())
                .eq(request.getEnrollStatus() != null, ActivityEnroll::getEnrollStatus, request.getEnrollStatus())
                .isNull(signedIn != null && signedIn == 0, ActivityEnroll::getSignInTime)
                .isNotNull(signedIn != null && signedIn == 1, ActivityEnroll::getSignInTime)
                .orderByDesc(ActivityEnroll::getCreateTime);

        Page<ActivityEnroll> page = activityEnrollRepository.selectPage(
                AdminPages.of(request.getPageNo(), request.getPageSize()),
                queryWrapper
        );

        Map<String, String> userNames = loadUserNames(
                page.getRecords().stream().map(ActivityEnroll::getUserId).collect(Collectors.toSet()));
        Map<Long, String> activityTitles = loadActivityTitles(
                page.getRecords().stream().map(ActivityEnroll::getActivityId).collect(Collectors.toSet()));

        Page<AdminEnrollmentVO> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream()
                .map(enrollment -> toVO(enrollment,
                        userNames.get(enrollment.getUserId()),
                        activityTitles.get(enrollment.getActivityId())))
                .toList());
        return PageResponse.from(responsePage);
    }

    private Map<String, String> loadUserNames(Collection<String> userIds) {
        Set<String> ids = userIds.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, UserInfo::getUserName, (a, b) -> a));
    }

    private Map<Long, String> loadActivityTitles(Collection<Long> activityIds) {
        Set<Long> ids = activityIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return activityMainRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, ActivityMain::getActivityTitle, (a, b) -> a));
    }

    private AdminEnrollmentVO toVO(ActivityEnroll enrollment, String userName, String activityTitle) {
        return AdminEnrollmentVO.builder()
                .enrollId(enrollment.getEnrollId())
                .userId(enrollment.getUserId())
                .userName(userName)
                .activityId(enrollment.getActivityId())
                .activityTitle(activityTitle)
                .enrollStatus(enrollment.getEnrollStatus())
                .signInTime(enrollment.getSignInTime())
                .feedbackScore(enrollment.getFeedbackScore())
                .feedbackContent(enrollment.getFeedbackContent())
                .createTime(enrollment.getCreateTime())
                .build();
    }
}
