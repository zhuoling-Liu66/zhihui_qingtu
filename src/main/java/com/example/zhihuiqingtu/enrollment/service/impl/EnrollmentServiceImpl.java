package com.example.zhihuiqingtu.enrollment.service.impl;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.TransactionEvents;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.award.dto.ActivityParticipantQueryRequest;
import com.example.zhihuiqingtu.enrollment.dto.CreateEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.EnrollmentQueryRequest;
import com.example.zhihuiqingtu.enrollment.dto.FeedbackEnrollmentRequest;
import com.example.zhihuiqingtu.enrollment.dto.UpdateAttendanceRequest;
import com.example.zhihuiqingtu.award.dto.UpdateAwardRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityCreditTier;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.enrollment.enums.AttendanceActionEnum;
import com.example.zhihuiqingtu.award.enums.AwardStatusEnum;
import com.example.zhihuiqingtu.award.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enrollment.enums.EnrollmentStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.award.service.ParticipantAwardService;
import com.example.zhihuiqingtu.award.support.AwardRecordFactory;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.enrollment.service.EnrollmentService;
import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantVO;
import com.example.zhihuiqingtu.award.vo.ActivityParticipantsResponse;
import com.example.zhihuiqingtu.enrollment.vo.EnrollmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.support.ActivityStockService;
import com.example.zhihuiqingtu.activity.support.HotActivityService;
import com.example.zhihuiqingtu.exception.GlobalExceptionHandler;
import com.example.zhihuiqingtu.validation.CreditUnit;

/**
 * 报名服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityMainRepository activityMainRepository;
    private final UserInfoRepository userInfoRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final ParticipantAwardService participantAwardService;
    private final AwardRecordFactory awardRecordFactory;
    /** Redis 名额闸门：把并发挡在数据库外面（性能），不负责正确性（正确性由条件 UPDATE 保证）。 */
    private final ActivityStockService activityStockService;
    /** 报名会改 current_participant，而它是活动详情的一个字段，所以必须让详情缓存失效。 */
    private final ActivityCacheService activityCacheService;
    /** 热门活动榜（Redis ZSet）。 */
    private final HotActivityService hotActivityService;
    /**
     * 当前登录用户读取入口（基于 Spring Security 的 SecurityContextHolder）。
     * <p>
     * 用它替代原来从请求体/请求参数读 userId —— 那些值是客户端自报的，
     * 任何人都能填别人的学号，以别人的名义报名、取消、签到、评价，
     * 或者查出全平台的报名数据。身份只有服务端从 JWT 解析出来的才可信。
     */
    private final CurrentUser currentUser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse createEnrollment(CreateEnrollmentRequest request) {
        // ★ 报名人 = 当前登录用户，不再读 request.getUserId()
        // 原写法 requireUser(request.getUserId()) 只是"校验这个学号存在"，

        String currentUserId = currentUser.requireUserId();
        requireUser(currentUserId);
        ActivityMain activityMain = requireActivity(request.getActivityId());
        // 审核通过前活动不公开，也不能被报名；否则待审核活动会被提前占位。
        if (!ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动尚未通过审核，暂不可报名");
        }
        if (!ActivityStatusEnum.PUBLISHED.getCode().equals(activityMain.getActivityStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前活动不可报名");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(ErrorCode.CONFLICT, "已结束活动不可报名");
        }
        // 查重也改用当前登录学号：否则攻击者可以拿别人的学号来试探"某人是否已报名"，
        // 而且查重对象与实际写入的人不一致时会漏判/误判。
        if (activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, currentUserId)
                .eq(ActivityEnroll::getActivityId, request.getActivityId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "请勿重复报名");
        }

        // ★ 关键顺序：先原子占用名额（拿到 activity_main 该行的 X 锁），再 INSERT 报名记录。
        //
        // 这个顺序同时修掉了两个并发问题（推导见 ActivityMainRepository#tryOccupySeat 的注释）：
        //   ① 超卖：原来的 "SELECT 判断 → Java 里 +1 → updateById" 是快照读 + check-then-act，
        //      两个并发请求会都读到旧值、都判定"没满"。现在判断被压进 UPDATE 的 WHERE，
        //      是当前读，后到的事务拿到锁后会重新求值 → 影响行数 0 → 正确拒绝。
        //   ② 死锁：activity_enroll 有外键指向 activity_main，INSERT 子表时 InnoDB 会在父表
        //      该行加 S 锁。先 INSERT 会导致两个事务各持 S 锁、再互相等对方释放以拿 X 锁，
        //      形成循环等待。先 UPDATE 则一开始就独占 X 锁，后来者只能在锁上排队，不会死锁。
        //
        // 注意：这里**不能**再用 activityMain.setCurrentParticipant(...) + updateById，
        // 那个写法除了上述两个问题，还会把实体所有字段写回，造成丢失更新（覆盖别人改的标题等）。

        boolean limited = activityStockService.isLimited(activityMain);

        // ── 第 1 层：Redis 前置闸门（性能，可选的加速器）──
        // 限流人数的活动才需要；不限人数时 isLimited 为 false，直接跳过，避免误扣一个不存在的 key。
        // Redis 判定"满了"就直接返回，请求根本不会碰到 MySQL 的行锁 —— 这才是它挡掉的那部分压力。
        if (limited && !activityStockService.tryDeduct(request.getActivityId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动报名人数已满");
        }

        // 从这一刻起 Redis 已经预扣了一个名额。只要本事务最终没有提交成功
        //（数据库满员、唯一索引冲突、任何后续异常），都统一在这里补回去。
        // 只注册一次补偿动作，避免分散在各 catch 分支里漏补或重复补。
        if (limited) {
            TransactionEvents.afterRollback(
                    "报名名额回补 activityId=" + request.getActivityId(),
                    () -> activityStockService.rollback(request.getActivityId())
            );
        }

        // ── 第 2 层：DB 原子扣减（正确性，真正的防线）──
        int occupied = activityMainRepository.tryOccupySeat(request.getActivityId());
        if (occupied == 0) {
            // 抛出异常 → 事务回滚 → 上面注册的回滚补偿会把 Redis 名额还回去
            throw new BusinessException(ErrorCode.CONFLICT, "活动报名人数已满");
        }

        ActivityEnroll enrollment = ActivityEnroll.builder()
                .userId(currentUserId)
                .activityId(request.getActivityId())
                .enrollStatus(EnrollmentStatusEnum.ENROLLED.getCode())
                .build();
        try {
            activityEnrollRepository.insert(enrollment);
        } catch (DuplicateKeyException exception) {
            // 唯一索引 uk_activity_enroll_user (user_id, activity_id, delete_flag) 兜底。
            // 上面的 selectCount 检查有并发窗口，真正拦住重复报名的是这个索引。
            // 不转换的话它会一路冒泡到 GlobalExceptionHandler 的兜底分支，变成 5000 + 一长串 SQL 报错。
            // Redis 回补由上面的 afterRollback 统一处理，这里不再手工补一次。
            throw new BusinessException(ErrorCode.CONFLICT, "请勿重复报名");
        }

        // 报名成功 → 两处外部状态要跟着动，但它们必须等数据库真正提交后再改：
        //   1. 活动详情里的 currentParticipant 变了，详情缓存必须失效；
        //   2. 热门榜分数 +1。
        // 若在提交前就执行，事务回滚时榜单已经加过分，且并发读会把旧数据重新写回缓存。
        // 库存 key 不用管，上面的 tryDeduct 已经扣过了。
        Long activityId = request.getActivityId();
        TransactionEvents.afterCommit("报名成功后失效缓存与热门榜 activityId=" + activityId, () -> {
            activityCacheService.evict(activityId);
            hotActivityService.incrementEnroll(activityId);
        });

        // 重新查一次，让返回体里的 currentParticipant 是占位后的真实值
        return toEnrollmentResponse(enrollment, activityMainRepository.selectById(activityId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse cancelEnrollment(Long enrollId) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        // ★ 操作者 = 当前登录用户。归属校验保留，但比较对象换成真实身份：
        // 原来 userId 是 @RequestParam 传进来的，攻击者只要把 userId 填成记录归属人就能取消别人的报名。
        // 现在这个值来自 JWT，改不了，才是真正的"仅可退出自己"。
        if (!enrollment.getUserId().equals(currentUser.requireUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可退出自己的活动报名记录");
        }

        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        if (!EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())
                || enrollment.getSignInTime() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "已完成的活动不能退出");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(ErrorCode.CONFLICT, "已结束活动不能退出");
        }

        // 同样用原子 SQL，而不是 "读出来 -1 再 updateById"（那是同一个 check-then-act 陷阱，
        // 并发退出时会少减，且 updateById 会把整行实体字段覆盖回去）。
        //
        // 取消的关键是"只有仍然是已报名、且未签到，才能取消并释放名额"。
        // 上面的 if 只是给用户一个更友好的提示，真正防止重复取消的是下面这条条件 UPDATE：
        // 两个并发取消只有第一个影响 1 行，第二个影响 0 行 → 抛冲突 → 不再执行 releaseSeat，
        // 名额只会被释放一次。
        String currentUserId = currentUser.requireUserId();
        int canceled = activityEnrollRepository.cancelIfEnrolled(enrollId, currentUserId);
        if (canceled == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "报名已取消或状态已变化，请刷新后重试");
        }
        activityMainRepository.releaseSeat(enrollment.getActivityId());

        Long activityId = enrollment.getActivityId();
        boolean limited = activityStockService.isLimited(activityMain);
        // 数据库提交成功后才回补 Redis 名额、失效缓存、热门榜减分。
        // 提交前改 Redis 的话，事务一旦回滚，Redis 会凭空多出一个名额（与数据库不一致）。
        TransactionEvents.afterCommit("取消报名后回补库存/失效缓存/热门榜减分 activityId=" + activityId, () -> {
            if (limited) {
                activityStockService.rollback(activityId);
            }
            activityCacheService.evict(activityId);
            hotActivityService.decrementEnroll(activityId);
        });

        return toEnrollmentResponse(enrollment, activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse signIn(Long enrollId) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        // ★ 签到人 = 当前登录用户，不再读请求参数里的 userId。
        // 签到会写签到时间，若身份可伪造，就能替别人刷考勤。
        if (!enrollment.getUserId().equals(currentUser.requireUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可签到自己的活动报名记录");
        }
        if (!EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前报名状态不允许签到");
        }

        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        if (!ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动尚未通过审核，暂不可签到");
        }
        if (!ActivityStatusEnum.PUBLISHED.getCode().equals(activityMain.getActivityStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前活动不可签到");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(ErrorCode.CONFLICT, "已结束活动不能签到");
        }
        if (!Integer.valueOf(1).equals(activityMain.getSignInEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动暂未开放签到");
        }

        // 签到同样用条件 UPDATE：只有当前仍是"已报名"才能变成"已签到"。
        //
        // 原来的 updateById 不检查当前状态，两个并发签到都会通过上面的 if 判断。
        // 现在第二个请求影响 0 行，直接返回冲突。
        // 签到只记录考勤，不再生成学分：完成与发放都推迟到签退。
        LocalDateTime signInTime = LocalDateTime.now();
        int signedIn = activityEnrollRepository.signInIfEnrolled(
                enrollId,
                currentUser.requireUserId(),
                signInTime
        );
        if (signedIn == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前报名状态不允许签到");
        }

        // 重新读取，返回体里的签到时间/状态来自数据库真实值，而不是内存中手工设置的对象
        return toEnrollmentResponse(requireEnrollment(enrollId), activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse signOut(Long enrollId) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        // ★ 签退人 = 当前登录用户，归属校验不可省。
        if (!enrollment.getUserId().equals(currentUser.requireUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可签退自己的活动报名记录");
        }
        if (!EnrollmentStatusEnum.SIGNED_IN.getCode().equals(enrollment.getEnrollStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先签到后再签退");
        }

        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        // 签退是对已发生的参与行为的收尾，不依赖签到开关，也不因活动已到结束时间而失效；
        // 只有活动被取消时禁止。
        if (ActivityStatusEnum.CANCELED.getCode().equals(activityMain.getActivityStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已取消活动不能签退");
        }

        LocalDateTime signOutTime = LocalDateTime.now();
        int completed = activityEnrollRepository.signOutIfSignedIn(
                enrollId,
                currentUser.requireUserId(),
                signOutTime
        );
        if (completed == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前报名状态不允许签退");
        }

        awardRecordFactory.generate(enrollment, activityMain);
        return toEnrollmentResponse(requireEnrollment(enrollId), activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse feedback(Long enrollId, FeedbackEnrollmentRequest request) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        // ★ 评价人 = 当前登录用户，不再读 request.getUserId()
        // 原写法允许把 userId 填成记录归属人，从而替别人提交评价（篡改他人的活动评分）。
        if (!enrollment.getUserId().equals(currentUser.requireUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可评价自己的活动记录");
        }
        if (enrollment.getSignOutTime() == null
                && !EnrollmentStatusEnum.COMPLETED.getCode().equals(enrollment.getEnrollStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先完成签退后再评价活动");
        }

        enrollment.setFeedbackScore(request.getFeedbackScore());
        enrollment.setFeedbackContent(request.getFeedbackContent());
        activityEnrollRepository.updateById(enrollment);
        return toEnrollmentResponse(enrollment, requireActivity(enrollment.getActivityId()));
    }

    @Override
    public PageResponse<EnrollmentResponse> queryEnrollments(EnrollmentQueryRequest request) {
        // ★ 过滤条件 = 当前登录学号，无条件加上，而不是"请求里传了才过滤"。
        // 原来的 .eq(request.getUserId() != null, ...) 两个问题：
        //   ① 不传 userId 时条件被跳过 → 一次请求就能拉走全平台的报名数据（越权读取）；
        //   ② 传别人的 userId → 直接查看他人的报名记录。
        // 现在身份来自 JWT，用户只能看到自己的报名记录，activityId / enrollStatus 仍可自由组合过滤。
        LambdaQueryWrapper<ActivityEnroll> queryWrapper = new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, currentUser.requireUserId())
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

    @Override
    public ActivityParticipantsResponse queryActivityParticipants(Long activityId,
                                                                 ActivityParticipantQueryRequest request) {
        return participantAwardService.queryActivityParticipants(activityId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityParticipantVO updateAttendance(Long enrollId, UpdateAttendanceRequest request) {
        return participantAwardService.updateAttendance(enrollId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityParticipantVO updateAward(Long enrollId, UpdateAwardRequest request) {
        return participantAwardService.updateAward(enrollId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmAwards(Long activityId) {
        return participantAwardService.confirmAwards(activityId);
    }

    private EnrollmentResponse toEnrollmentResponse(ActivityEnroll enrollment) {
        return toEnrollmentResponse(enrollment, activityMainRepository.selectById(enrollment.getActivityId()));
    }

    private EnrollmentResponse toEnrollmentResponse(ActivityEnroll enrollment, ActivityMain activityMain) {
        Integer displayStatus = activityMain == null ? null : resolveDisplayStatus(activityMain);
        boolean activityCanceled = activityMain != null
                && ActivityStatusEnum.CANCELED.getCode().equals(activityMain.getActivityStatus());
        boolean canSignIn = activityMain != null
                && Integer.valueOf(1).equals(activityMain.getSignInEnabled())
                && ActivityStatusEnum.PUBLISHED.getCode().equals(activityMain.getActivityStatus())
                && ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())
                && EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())
                && enrollment.getSignInTime() == null
                && !ActivityStatusEnum.FINISHED.getCode().equals(displayStatus);
        boolean canSignOut = !activityCanceled
                && EnrollmentStatusEnum.SIGNED_IN.getCode().equals(enrollment.getEnrollStatus())
                && enrollment.getSignOutTime() == null;
        boolean canCancel = EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())
                && enrollment.getSignInTime() == null
                && activityMain != null
                && !ActivityStatusEnum.FINISHED.getCode().equals(displayStatus);
        boolean canFeedback = enrollment.getSignOutTime() != null
                && (!StringUtils.hasText(enrollment.getFeedbackContent()) || enrollment.getFeedbackScore() == null);

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
                .signOutTime(enrollment.getSignOutTime())
                .feedbackScore(enrollment.getFeedbackScore())
                .feedbackContent(enrollment.getFeedbackContent())
                .canSignIn(canSignIn)
                .canSignOut(canSignOut)
                .canCancel(canCancel)
                .canFeedback(canFeedback)
                .createTime(enrollment.getCreateTime())
                .build();
    }

    private Integer resolveDisplayStatus(ActivityMain activityMain) {
        return ActivityStatusEnum.resolveDisplayStatus(activityMain.getActivityStatus(), activityMain.getEndTime());
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
