package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.enrollment.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.dto.enrollment.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.dto.enrollment.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.entity.ActivityEnroll;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.enums.EnrollmentStatusEnum;
import com.example.zhihuiqingtu.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.service.EnrollmentService;
import com.example.zhihuiqingtu.vo.enrollment.EnrollmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 报名服务实现。
 */
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityMainRepository activityMainRepository;
    private final UserInfoRepository userInfoRepository;
    private final CreditRecordRepository creditRecordRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse createEnrollment(CreateEnrollmentRequest request) {
        requireUser(request.getUserId());
        ActivityMain activityMain = requireActivity(request.getActivityId());
        if (!ActivityStatusEnum.PUBLISHED.getCode().equals(activityMain.getActivityStatus())) {
            throw new BusinessException(4201, "当前活动不可报名");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(4202, "已结束活动不可报名");
        }
        if (activityMain.getMaxParticipant() != null
                && activityMain.getMaxParticipant() > 0
                && activityMain.getCurrentParticipant() >= activityMain.getMaxParticipant()) {
            throw new BusinessException(4203, "活动报名人数已满");
        }
        if (activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, request.getUserId())
                .eq(ActivityEnroll::getActivityId, request.getActivityId())) > 0) {
            throw new BusinessException(4204, "请勿重复报名");
        }

        ActivityEnroll enrollment = ActivityEnroll.builder()
                .userId(request.getUserId())
                .activityId(request.getActivityId())
                .enrollStatus(EnrollmentStatusEnum.ENROLLED.getCode())
                .build();
        activityEnrollRepository.insert(enrollment);
        activityMain.setCurrentParticipant(activityMain.getCurrentParticipant() + 1);
        activityMainRepository.updateById(activityMain);
        return toEnrollmentResponse(enrollment, activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse cancelEnrollment(Long enrollId, String userId) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException(4205, "仅可退出自己的活动报名记录");
        }

        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        if (EnrollmentStatusEnum.SIGNED_IN.getCode().equals(enrollment.getEnrollStatus())
                || EnrollmentStatusEnum.COMPLETED.getCode().equals(enrollment.getEnrollStatus())
                || enrollment.getSignInTime() != null) {
            throw new BusinessException(4206, "已签到或已完成的活动不能退出");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(4207, "已结束活动不能退出");
        }

        activityEnrollRepository.deleteById(enrollId);
        rollbackActivityCount(enrollment.getActivityId());
        return toEnrollmentResponse(enrollment, activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse signIn(Long enrollId, String userId) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException(4208, "仅可签到自己的活动报名记录");
        }
        if (!EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())) {
            throw new BusinessException(4209, "当前报名状态不允许签到");
        }

        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        if (!ActivityStatusEnum.PUBLISHED.getCode().equals(activityMain.getActivityStatus())) {
            throw new BusinessException(4210, "当前活动不可签到");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(4211, "已结束活动不能签到");
        }
        if (!Integer.valueOf(1).equals(activityMain.getSignInEnabled())) {
            throw new BusinessException(4212, "活动暂未开放签到");
        }

        enrollment.setEnrollStatus(EnrollmentStatusEnum.SIGNED_IN.getCode());
        enrollment.setSignInTime(LocalDateTime.now());
        activityEnrollRepository.updateById(enrollment);
        return toEnrollmentResponse(enrollment, activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse feedback(Long enrollId, FeedbackEnrollmentRequest request) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        if (!EnrollmentStatusEnum.SIGNED_IN.getCode().equals(enrollment.getEnrollStatus())
                && !EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())) {
            throw new BusinessException(4213, "当前报名状态不允许提交评价");
        }

        enrollment.setFeedbackScore(request.getFeedbackScore());
        enrollment.setFeedbackContent(request.getFeedbackContent());
        enrollment.setEnrollStatus(EnrollmentStatusEnum.COMPLETED.getCode());
        activityEnrollRepository.updateById(enrollment);
        generateCreditRecord(enrollment);
        return toEnrollmentResponse(enrollment, requireActivity(enrollment.getActivityId()));
    }

    @Override
    public PageResponse<EnrollmentResponse> queryEnrollments(EnrollmentQueryRequest request) {
        LambdaQueryWrapper<ActivityEnroll> queryWrapper = new LambdaQueryWrapper<ActivityEnroll>()
                .eq(request.getUserId() != null, ActivityEnroll::getUserId, request.getUserId())
                .eq(request.getActivityId() != null, ActivityEnroll::getActivityId, request.getActivityId())
                .eq(request.getEnrollStatus() != null, ActivityEnroll::getEnrollStatus, request.getEnrollStatus())
                .orderByDesc(ActivityEnroll::getCreateTime);

        Page<ActivityEnroll> page = activityEnrollRepository.selectPage(
                new Page<>(request.getPageNo() + 1L, request.getPageSize()),
                queryWrapper
        );
        Page<EnrollmentResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toEnrollmentResponse).toList());
        return PageResponse.from(responsePage);
    }

    private void generateCreditRecord(ActivityEnroll enrollment) {
        if (creditRecordRepository.selectCount(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, enrollment.getUserId())
                .eq(CreditRecord::getActivityId, enrollment.getActivityId())) > 0) {
            return;
        }
        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityMain.getActivityType())) {
            return;
        }
        CreditRecord creditRecord = CreditRecord.builder()
                .userId(enrollment.getUserId())
                .activityId(enrollment.getActivityId())
                .credit(activityMain.getActivityCredit())
                .creditType(activityMain.getCreditType())
                .verifyStatus(ReviewStatusEnum.PENDING.getCode())
                .build();
        creditRecordRepository.insert(creditRecord);
    }

    private void rollbackActivityCount(Long activityId) {
        ActivityMain activityMain = requireActivity(activityId);
        int currentCount = activityMain.getCurrentParticipant() == null ? 0 : activityMain.getCurrentParticipant();
        activityMain.setCurrentParticipant(Math.max(0, currentCount - 1));
        activityMainRepository.updateById(activityMain);
    }

    private EnrollmentResponse toEnrollmentResponse(ActivityEnroll enrollment) {
        return toEnrollmentResponse(enrollment, activityMainRepository.selectById(enrollment.getActivityId()));
    }

    private EnrollmentResponse toEnrollmentResponse(ActivityEnroll enrollment, ActivityMain activityMain) {
        Integer displayStatus = activityMain == null ? null : resolveDisplayStatus(activityMain);
        boolean canSignIn = activityMain != null
                && Integer.valueOf(1).equals(activityMain.getSignInEnabled())
                && ActivityStatusEnum.PUBLISHED.getCode().equals(activityMain.getActivityStatus())
                && EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())
                && !ActivityStatusEnum.FINISHED.getCode().equals(displayStatus);
        boolean canCancel = !EnrollmentStatusEnum.SIGNED_IN.getCode().equals(enrollment.getEnrollStatus())
                && !EnrollmentStatusEnum.COMPLETED.getCode().equals(enrollment.getEnrollStatus())
                && enrollment.getSignInTime() == null
                && activityMain != null
                && !ActivityStatusEnum.FINISHED.getCode().equals(displayStatus);

        return EnrollmentResponse.builder()
                .enrollId(enrollment.getEnrollId())
                .userId(enrollment.getUserId())
                .activityId(enrollment.getActivityId())
                .activityTitle(activityMain == null ? null : activityMain.getActivityTitle())
                .activityCoverUrl(activityMain == null ? null : activityMain.getCoverUrl())
                .activityLocation(activityMain == null ? null : activityMain.getActivityLocation())
                .startTime(activityMain == null ? null : activityMain.getStartTime())
                .endTime(activityMain == null ? null : activityMain.getEndTime())
                .activityStatus(activityMain == null ? null : activityMain.getActivityStatus())
                .displayStatus(displayStatus)
                .signInEnabled(activityMain == null ? 0 : activityMain.getSignInEnabled())
                .enrollStatus(enrollment.getEnrollStatus())
                .signInTime(enrollment.getSignInTime())
                .feedbackScore(enrollment.getFeedbackScore())
                .feedbackContent(enrollment.getFeedbackContent())
                .canSignIn(canSignIn)
                .canCancel(canCancel)
                .createTime(enrollment.getCreateTime())
                .build();
    }

    private Integer resolveDisplayStatus(ActivityMain activityMain) {
        return ActivityStatusEnum.resolveDisplayStatus(activityMain.getActivityStatus(), activityMain.getEndTime());
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(4009, "用户不存在");
        }
        return userInfo;
    }

    private ActivityMain requireActivity(Long activityId) {
        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            throw new BusinessException(4100, "活动不存在");
        }
        return activityMain;
    }

    private ActivityEnroll requireEnrollment(Long enrollId) {
        ActivityEnroll enrollment = activityEnrollRepository.selectById(enrollId);
        if (enrollment == null) {
            throw new BusinessException(4200, "报名记录不存在");
        }
        return enrollment;
    }
}
