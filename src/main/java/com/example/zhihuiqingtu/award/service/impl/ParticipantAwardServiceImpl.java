package com.example.zhihuiqingtu.award.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.activity.entity.ActivityCreditTier;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import com.example.zhihuiqingtu.award.dto.ActivityParticipantQueryRequest;
import com.example.zhihuiqingtu.award.dto.UpdateAwardRequest;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.award.enums.AwardStatusEnum;
import com.example.zhihuiqingtu.award.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.award.service.ParticipantAwardService;
import com.example.zhihuiqingtu.award.support.AwardRecordFactory;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantVO;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantsResponse;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.enrollment.dto.UpdateAttendanceRequest;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.enrollment.enums.AttendanceActionEnum;
import com.example.zhihuiqingtu.enrollment.enums.EnrollmentStatusEnum;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 参与人员与发放：名单查询、考勤修正、按人发放、批量确认。
 */
@Service
@RequiredArgsConstructor
public class ParticipantAwardServiceImpl implements ParticipantAwardService {

    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityMainRepository activityMainRepository;
    private final ActivityCreditTierRepository activityCreditTierRepository;
    private final UserInfoRepository userInfoRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final CurrentUser currentUser;
    private final AwardRecordFactory awardRecordFactory;

    private Integer resolveDisplayStatus(ActivityMain activityMain) {
        return ActivityStatusEnum.resolveDisplayStatus(activityMain.getActivityStatus(), activityMain.getEndTime());
    }

    @Override
    public ActivityParticipantsResponse queryActivityParticipants(Long activityId,
                                                                 ActivityParticipantQueryRequest request) {
        ActivityMain activity = requireActivity(activityId);
        requireActivityManagePermission(activity);

        int pageNo = Math.max(request.getPageNo() == null ? 0 : request.getPageNo(), 0);
        int pageSize = Math.min(Math.max(request.getPageSize() == null ? 10 : request.getPageSize(), 1), 100);

        LambdaQueryWrapper<ActivityEnroll> queryWrapper = new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getActivityId, activityId)
                .eq(request.getEnrollStatus() != null, ActivityEnroll::getEnrollStatus, request.getEnrollStatus())
                .orderByDesc(ActivityEnroll::getCreateTime);
        Page<ActivityEnroll> page = activityEnrollRepository.selectPage(
                new Page<>(pageNo + 1L, pageSize),
                queryWrapper
        );

        List<CreditRecord> allAwards = creditRecordRepository.selectList(
                new LambdaQueryWrapper<CreditRecord>()
                        .eq(CreditRecord::getActivityId, activityId));
        Map<String, CreditRecord> awardMap = allAwards.stream()
                .collect(Collectors.toMap(CreditRecord::getUserId, record -> record, (a, b) -> a));
        Map<String, String> userNames = loadUserNames(
                page.getRecords().stream().map(ActivityEnroll::getUserId).toList());

        Page<ActivityParticipantVO> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream()
                .map(enrollment -> toParticipantVO(
                        enrollment,
                        awardMap.get(enrollment.getUserId()),
                        userNames.get(enrollment.getUserId())))
                .toList());

        return ActivityParticipantsResponse.builder()
                .activityId(activity.getActivityId())
                .activityTitle(activity.getActivityTitle())
                .activityType(activity.getActivityType())
                .activityCredit(activity.getActivityCredit())
                .creditType(activity.getCreditType())
                .creditMode(activity.getCreditMode())
                .creditTiers(loadTierUsage(activity, allAwards))
                .volunteerHours(activity.getVolunteerHours())
                .volunteerMode(activity.getVolunteerMode())
                .reviewStatus(activity.getReviewStatus())
                .displayStatus(resolveDisplayStatus(activity))
                .signInEnabled(activity.getSignInEnabled())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .totalEnrolled(activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                        .eq(ActivityEnroll::getActivityId, activityId)))
                .signedInCount(activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                        .eq(ActivityEnroll::getActivityId, activityId)
                        .isNotNull(ActivityEnroll::getSignInTime)))
                .completedCount(activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                        .eq(ActivityEnroll::getActivityId, activityId)
                        .eq(ActivityEnroll::getEnrollStatus, EnrollmentStatusEnum.COMPLETED.getCode())))
                .pendingAwardCount(countAwards(activityId, AwardStatusEnum.PENDING.getCode()))
                .reviewingAwardCount(countAwards(activityId, AwardStatusEnum.PENDING_REVIEW.getCode()))
                .approvedAwardCount(countAwards(activityId, AwardStatusEnum.APPROVED.getCode()))
                .rejectedAwardCount(countAwards(activityId, AwardStatusEnum.REJECTED.getCode()))
                .page(PageResponse.from(responsePage))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityParticipantVO updateAttendance(Long enrollId, UpdateAttendanceRequest request) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        ActivityMain activity = requireActivity(enrollment.getActivityId());
        requireActivityManagePermission(activity);
        AttendanceActionEnum action = AttendanceActionEnum.fromCode(request.getAction());
        if (action == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的考勤操作");
        }
        if (ActivityStatusEnum.CANCELED.getCode().equals(activity.getActivityStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已取消活动不能修改考勤");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = switch (action) {
            case MARK_SIGN_IN -> activityEnrollRepository.markSignIn(enrollId, now);
            case MARK_SIGN_OUT -> {
                int changed = activityEnrollRepository.markSignOut(enrollId, now);
                if (changed > 0) {
                    awardRecordFactory.generate(requireEnrollment(enrollId), activity);
                }
                yield changed;
            }
            case REVERT_SIGN_IN -> activityEnrollRepository.revertSignIn(enrollId);
            case REVERT_SIGN_OUT -> {
                CreditRecord award = findAward(enrollment.getUserId(), activity.getActivityId());
                if (award != null && AwardStatusEnum.APPROVED.getCode().equals(award.getVerifyStatus())) {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "该参与者的发放已确认，请先撤销发放再撤销签退");
                }
                if (award != null) {
                    // 待确认/不发放的发放记录直接物理删除：逻辑删除会占用唯一索引
                    // (user_id, activity_id, delete_flag=1)，下一次循环会插不进去。
                    creditRecordRepository.deleteUnconfirmedAward(award.getRecordId());
                }
                yield activityEnrollRepository.revertSignOut(enrollId);
            }
        };
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不允许该考勤操作，请刷新后重试");
        }

        ActivityEnroll latest = requireEnrollment(enrollId);
        return toParticipantVO(latest,
                findAward(latest.getUserId(), activity.getActivityId()),
                requireUserName(latest.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityParticipantVO updateAward(Long enrollId, UpdateAwardRequest request) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        ActivityMain activity = requireActivity(enrollment.getActivityId());
        requireActivityManagePermission(activity);

        boolean creditActivity = ActivityTypeEnum.SECOND_CLASS.getCode().equals(activity.getActivityType());
        boolean volunteerActivity = ActivityTypeEnum.VOLUNTEER.getCode().equals(activity.getActivityType());
        if (!creditActivity && !volunteerActivity) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该活动类型不产生发放记录");
        }
        CreditRecord award = findAward(enrollment.getUserId(), activity.getActivityId());
        if (award == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该参与者还没有发放记录，请先完成签退");
        }

        if (creditActivity) {
            applyCreditAward(activity, award, request);
        } else {
            applyVolunteerAward(activity, award, request);
        }
        creditRecordRepository.updateById(award);
        return toParticipantVO(enrollment, award, requireUserName(enrollment.getUserId()));
    }

    /**
     * 学分发放：固定模式直接调整金额；组合模式必须选择已配置的档位，并受档位名额限制。
     */
    private void applyCreditAward(ActivityMain activity, CreditRecord award, UpdateAwardRequest request) {
        Integer targetStatus = request.getVerifyStatus();
        if (targetStatus != null
                && !AwardStatusEnum.APPROVED.getCode().equals(targetStatus)
                && !AwardStatusEnum.REJECTED.getCode().equals(targetStatus)
                && !AwardStatusEnum.PENDING.getCode().equals(targetStatus)
                && !AwardStatusEnum.PENDING_REVIEW.getCode().equals(targetStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学分发放结论仅支持发放或不发放");
        }
        boolean admin = currentUser.hasRole(UserRoleEnum.ADMIN.getCode());
        boolean pendingReview = AwardStatusEnum.PENDING_REVIEW.getCode().equals(award.getVerifyStatus());
        // AI 批量导入的记录处于“待审核”，运营不能自行通过或驳回，只能调整金额
        if (pendingReview && !admin && targetStatus != null
                && !AwardStatusEnum.PENDING_REVIEW.getCode().equals(targetStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该发放记录待管理员审核，运营不能直接通过或不发放");
        }
        boolean combo = Integer.valueOf(2).equals(activity.getCreditMode());
        if (combo) {
            if (AwardStatusEnum.REJECTED.getCode().equals(targetStatus)) {
                // 不发放不需要档位，同时释放该档名额
                award.setVerifyStatus(targetStatus);
                applyReviewMeta(award, request);
                return;
            }
            BigDecimal targetCredit = request.getCredit() != null ? request.getCredit() : award.getCredit();
            if (targetCredit == null || targetCredit.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择该参与者的学分档位");
            }
            ActivityCreditTier tier = requireMatchingTier(activity.getActivityId(), targetCredit);
            int used = countTierUsage(activity.getActivityId(), targetCredit, award.getRecordId());
            if (used >= tier.getQuota()) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "该学分档位名额已满（" + tier.getQuota() + " 名），请选择其他档位");
            }
            award.setCredit(targetCredit);
            if (request.getCreditType() != null) {
                requireValidCreditType(request.getCreditType());
                award.setCreditType(request.getCreditType());
            }
            award.setVolunteerHours(null);
        } else {
            if (request.getCredit() != null) {
                validateAwardCredit(request.getCredit());
                award.setCredit(request.getCredit());
            }
            if (request.getCreditType() != null) {
                requireValidCreditType(request.getCreditType());
                award.setCreditType(request.getCreditType());
            }
            award.setVolunteerHours(null);
        }
        if (targetStatus != null) {
            award.setVerifyStatus(targetStatus);
            applyReviewMeta(award, request);
        }
    }

    /**
     * 志愿时长发放：
     * <ul>
     *   <li>固定模式：发布者/管理员直接确认；</li>
     *   <li>灵活模式：发布者录入时长即“提交管理员审核”（状态 3），
     *       只有管理员能通过（1）或不发放（2）。</li>
     * </ul>
     */
    private void applyVolunteerAward(ActivityMain activity, CreditRecord award, UpdateAwardRequest request) {
        Integer targetStatus = request.getVerifyStatus();
        boolean admin = currentUser.hasRole(UserRoleEnum.ADMIN.getCode());
        boolean flexible = Integer.valueOf(2).equals(activity.getVolunteerMode());
        award.setCredit(BigDecimal.ZERO);
        award.setCreditType(null);

        if (!flexible) {
            boolean pendingReview = AwardStatusEnum.PENDING_REVIEW.getCode().equals(award.getVerifyStatus());
            if (pendingReview && !admin && targetStatus != null
                    && !AwardStatusEnum.PENDING_REVIEW.getCode().equals(targetStatus)) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "该发放记录待管理员审核，运营不能直接通过或不发放");
            }
            if (targetStatus != null
                    && !AwardStatusEnum.APPROVED.getCode().equals(targetStatus)
                    && !AwardStatusEnum.REJECTED.getCode().equals(targetStatus)
                    && !AwardStatusEnum.PENDING.getCode().equals(targetStatus)
                    && !AwardStatusEnum.PENDING_REVIEW.getCode().equals(targetStatus)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿时长发放结论仅支持发放或不发放");
            }
            if (request.getVolunteerHours() != null) {
                validateAwardHours(request.getVolunteerHours());
                award.setVolunteerHours(request.getVolunteerHours());
            }
            if (targetStatus != null) {
                award.setVerifyStatus(targetStatus);
                applyReviewMeta(award, request);
            }
            return;
        }

        // 灵活模式
        if (!admin && targetStatus != null
                && !AwardStatusEnum.PENDING_REVIEW.getCode().equals(targetStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "灵活时长由发布者录入后提交管理员审核，不能直接发放或不发放");
        }
        if (admin && targetStatus != null
                && !AwardStatusEnum.APPROVED.getCode().equals(targetStatus)
                && !AwardStatusEnum.REJECTED.getCode().equals(targetStatus)
                && !AwardStatusEnum.PENDING_REVIEW.getCode().equals(targetStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核结论仅支持通过、不发放或退回待审核");
        }
        BigDecimal targetHours = request.getVolunteerHours() != null
                ? request.getVolunteerHours()
                : award.getVolunteerHours();
        if (request.getVolunteerHours() != null) {
            validateAwardHours(request.getVolunteerHours());
            award.setVolunteerHours(request.getVolunteerHours());
        }

        if (!admin) {
            // 发布者保存即提交审核：必须已经录入时长
            if (targetHours == null || targetHours.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "请先录入该学生的志愿时长");
            }
            award.setVerifyStatus(AwardStatusEnum.PENDING_REVIEW.getCode());
            applyReviewMeta(award, request);
        } else if (targetStatus != null) {
            if (AwardStatusEnum.APPROVED.getCode().equals(targetStatus)
                    && (targetHours == null || targetHours.compareTo(BigDecimal.ZERO) <= 0)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "时长为空，不能通过审核");
            }
            award.setVerifyStatus(targetStatus);
            applyReviewMeta(award, request);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmAwards(Long activityId) {
        ActivityMain activity = requireActivity(activityId);
        requireActivityManagePermission(activity);
        boolean admin = currentUser.hasRole(UserRoleEnum.ADMIN.getCode());
        boolean creditActivity = ActivityTypeEnum.SECOND_CLASS.getCode().equals(activity.getActivityType());
        boolean volunteerActivity = ActivityTypeEnum.VOLUNTEER.getCode().equals(activity.getActivityType());
        if (!creditActivity && !volunteerActivity) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该活动类型不产生发放记录");
        }
        boolean comboCredit = creditActivity && Integer.valueOf(2).equals(activity.getCreditMode());
        boolean flexibleHours = volunteerActivity && Integer.valueOf(2).equals(activity.getVolunteerMode());

        // AI 导入产生的“待审核”记录：只有管理员可以批量通过（学分/固定志愿/灵活志愿通用）
        if (admin && countAwards(activityId, AwardStatusEnum.PENDING_REVIEW.getCode()) > 0) {
            return batchUpdateAwardStatus(activityId,
                    AwardStatusEnum.PENDING_REVIEW.getCode(), AwardStatusEnum.APPROVED.getCode(), null);
        }

        if (comboCredit) {
            List<CreditRecord> pending = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                    .eq(CreditRecord::getActivityId, activityId)
                    .eq(CreditRecord::getVerifyStatus, AwardStatusEnum.PENDING.getCode()));
            if (pending.isEmpty()) {
                throw new BusinessException(ErrorCode.CONFLICT, "没有待确认的发放记录");
            }
            long unassigned = pending.stream()
                    .filter(record -> record.getCredit() == null
                            || record.getCredit().compareTo(BigDecimal.ZERO) <= 0)
                    .count();
            if (unassigned > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "还有 " + unassigned + " 人未选择学分档位，请逐个选择后再批量确认");
            }
            return batchUpdateAwardStatus(activityId,
                    AwardStatusEnum.PENDING.getCode(), AwardStatusEnum.APPROVED.getCode(), null);
        }

        if (flexibleHours) {
            if (admin) {
                List<CreditRecord> submitted = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                        .eq(CreditRecord::getActivityId, activityId)
                        .eq(CreditRecord::getVerifyStatus, AwardStatusEnum.PENDING_REVIEW.getCode()));
                if (submitted.isEmpty()) {
                    throw new BusinessException(ErrorCode.CONFLICT, "没有待审核的发放记录");
                }
                long missing = submitted.stream()
                        .filter(record -> record.getVolunteerHours() == null
                                || record.getVolunteerHours().compareTo(BigDecimal.ZERO) <= 0)
                        .count();
                if (missing > 0) {
                    throw new BusinessException(ErrorCode.CONFLICT, "还有 " + missing + " 人未录入志愿时长");
                }
                return batchUpdateAwardStatus(activityId,
                        AwardStatusEnum.PENDING_REVIEW.getCode(), AwardStatusEnum.APPROVED.getCode(), null);
            }
            List<CreditRecord> pending = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                    .eq(CreditRecord::getActivityId, activityId)
                    .eq(CreditRecord::getVerifyStatus, AwardStatusEnum.PENDING.getCode()));
            if (pending.isEmpty()) {
                throw new BusinessException(ErrorCode.CONFLICT, "没有待提交的发放记录");
            }
            long missing = pending.stream()
                    .filter(record -> record.getVolunteerHours() == null
                            || record.getVolunteerHours().compareTo(BigDecimal.ZERO) <= 0)
                    .count();
            if (missing > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "还有 " + missing + " 人未录入志愿时长，请逐个录入后再批量提交");
            }
            return batchUpdateAwardStatus(activityId,
                    AwardStatusEnum.PENDING.getCode(), AwardStatusEnum.PENDING_REVIEW.getCode(), null);
        }

        int updated = batchUpdateAwardStatus(activityId,
                AwardStatusEnum.PENDING.getCode(), AwardStatusEnum.APPROVED.getCode(), null);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "没有待确认的发放记录");
        }
        return updated;
    }

    private int batchUpdateAwardStatus(Long activityId, Integer fromStatus, Integer toStatus, String rejectReason) {
        return creditRecordRepository.update(null, new LambdaUpdateWrapper<CreditRecord>()
                .eq(CreditRecord::getActivityId, activityId)
                .eq(CreditRecord::getVerifyStatus, fromStatus)
                .set(CreditRecord::getVerifyStatus, toStatus)
                .set(CreditRecord::getVerifyUserId, currentUser.requireUserId())
                .set(CreditRecord::getVerifyTime, LocalDateTime.now())
                .set(CreditRecord::getRejectReason, rejectReason));
    }

    private ActivityParticipantVO toParticipantVO(ActivityEnroll enrollment, CreditRecord award, String userName) {
        return ActivityParticipantVO.builder()
                .enrollId(enrollment.getEnrollId())
                .userId(enrollment.getUserId())
                .userName(userName)
                .enrollStatus(enrollment.getEnrollStatus())
                .signInTime(enrollment.getSignInTime())
                .signOutTime(enrollment.getSignOutTime())
                .feedbackScore(enrollment.getFeedbackScore())
                .feedbackContent(enrollment.getFeedbackContent())
                .createTime(enrollment.getCreateTime())
                .recordId(award == null ? null : award.getRecordId())
                .credit(award == null ? null : award.getCredit())
                .creditType(award == null ? null : award.getCreditType())
                .volunteerHours(award == null ? null : award.getVolunteerHours())
                .verifyStatus(award == null ? null : award.getVerifyStatus())
                .rejectReason(award == null ? null : award.getRejectReason())
                .verifyTime(award == null ? null : award.getVerifyTime())
                .build();
    }

    /**
     * 参与人员管理权限：活动发布者本人，或管理员。
     *
     * <p>运营人员是其发布活动的发布者，因此天然具备自己活动的管理权限；
     * 其他人（包括已报名学生）一律拒绝。
     */
    private void requireActivityManagePermission(ActivityMain activity) {
        String currentUserId = currentUser.requireUserId();
        if (currentUserId.equals(activity.getPublisherUserId())
                || currentUser.hasRole(UserRoleEnum.ADMIN.getCode())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "仅活动发布者或管理员可以管理参与人员");
    }

    private CreditRecord findAward(String userId, Long activityId) {
        return creditRecordRepository.selectOne(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, userId)
                .eq(CreditRecord::getActivityId, activityId));
    }

    private Long countAwards(Long activityId, Integer verifyStatus) {
        return creditRecordRepository.selectCount(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getActivityId, activityId)
                .eq(CreditRecord::getVerifyStatus, verifyStatus));
    }

    private Map<String, String> loadUserNames(List<String> userIds) {
        Set<String> ids = userIds.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, UserInfo::getUserName, (a, b) -> a));
    }

    private String requireUserName(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        return userInfo == null ? null : userInfo.getUserName();
    }

    /**
     * 发放学分校验：大于 0，且以 0.05 为单位（与活动创建时的 CreditUnit 规则一致）。
     */
    private void validateAwardCredit(BigDecimal credit) {
        if (credit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "发放学分必须大于0");
        }
        BigDecimal normalized = credit.stripTrailingZeros();
        if (normalized.scale() > 2
                || normalized.remainder(new BigDecimal("0.05")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "发放学分必须以0.05为单位");
        }
    }

    private void validateAwardHours(BigDecimal hours) {
        if (hours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿时长必须大于0");
        }
    }

    private void applyReviewMeta(CreditRecord award, UpdateAwardRequest request) {
        award.setVerifyUserId(currentUser.requireUserId());
        award.setVerifyTime(LocalDateTime.now());
        award.setRejectReason(AwardStatusEnum.REJECTED.getCode().equals(award.getVerifyStatus())
                && StringUtils.hasText(request.getRejectReason())
                ? request.getRejectReason().trim()
                : null);
    }

    /**
     * 组合学分：请求的学分必须精确匹配活动配置的某个档位。
     */
    private ActivityCreditTier requireMatchingTier(Long activityId, BigDecimal credit) {
        return activityCreditTierRepository.selectList(new LambdaQueryWrapper<ActivityCreditTier>()
                        .eq(ActivityCreditTier::getActivityId, activityId))
                .stream()
                .filter(tier -> tier.getCredit().compareTo(credit) == 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR,
                        "所选学分不在活动配置的档位内，请重新选择"));
    }

    /**
     * 已占用名额：同一档位下“非不发放”的记录数（排除当前记录本身）。
     */
    private int countTierUsage(Long activityId, BigDecimal credit, Long excludeRecordId) {
        return (int) creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                        .eq(CreditRecord::getActivityId, activityId)
                        .eq(CreditRecord::getCredit, credit)
                        .ne(CreditRecord::getVerifyStatus, AwardStatusEnum.REJECTED.getCode()))
                .stream()
                .filter(record -> !record.getRecordId().equals(excludeRecordId))
                .count();
    }

    /**
     * 组合学分档位 + 已占用名额（不含“不发放”的记录）。
     */
    private List<CreditTierVO> loadTierUsage(ActivityMain activity, List<CreditRecord> awards) {
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(activity.getActivityType())
                || !Integer.valueOf(2).equals(activity.getCreditMode())) {
            return List.of();
        }
        return activityCreditTierRepository.selectList(new LambdaQueryWrapper<ActivityCreditTier>()
                        .eq(ActivityCreditTier::getActivityId, activity.getActivityId())
                        .orderByAsc(ActivityCreditTier::getSortNo))
                .stream()
                .map(tier -> CreditTierVO.builder()
                        .tierId(tier.getTierId())
                        .credit(tier.getCredit())
                        .quota(tier.getQuota())
                        .used((int) awards.stream()
                                .filter(award -> !AwardStatusEnum.REJECTED.getCode().equals(award.getVerifyStatus())
                                        && award.getCredit() != null
                                        && award.getCredit().compareTo(tier.getCredit()) == 0)
                                .count())
                        .build())
                .toList();
    }

    private void requireValidCreditType(Integer creditType) {
        for (CreditTypeEnum value : CreditTypeEnum.values()) {
            if (value.getCode().equals(creditType)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "学分类型不合法");
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return userInfo;
    }

    private ActivityMain requireActivity(Long activityId) {
        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activityMain;
    }

    private ActivityEnroll requireEnrollment(Long enrollId) {
        ActivityEnroll enrollment = activityEnrollRepository.selectById(enrollId);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报名记录不存在");
        }
        return enrollment;
    }
}
