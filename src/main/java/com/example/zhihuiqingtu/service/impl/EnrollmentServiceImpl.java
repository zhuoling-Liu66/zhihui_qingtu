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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    /** Redis 名额闸门：把并发挡在数据库外面（性能），不负责正确性（正确性由条件 UPDATE 保证）。 */
    private final ActivityStockService activityStockService;
    /** 报名会改 current_participant，而它是活动详情的一个字段，所以必须让详情缓存失效。 */
    private final ActivityCacheService activityCacheService;
    /** 热门活动榜（Redis ZSet）。 */
    private final HotActivityService hotActivityService;

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
        if (activityEnrollRepository.selectCount(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, request.getUserId())
                .eq(ActivityEnroll::getActivityId, request.getActivityId())) > 0) {
            throw new BusinessException(4204, "请勿重复报名");
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
            throw new BusinessException(4203, "活动报名人数已满");
        }

        // ── 第 2 层：DB 原子扣减（正确性，真正的防线）──
        int occupied = activityMainRepository.tryOccupySeat(request.getActivityId());
        if (occupied == 0) {
            // Redis 放行了但数据库说没名额：说明两者出现了短暂不一致（key 过期、Redis 重启、
            // 或并发下别的请求已经占掉）。此时必须【回补】刚预扣的那一个，否则这个名额被白扣，
            // 表现为"活动明明有空位却一直报人数已满"。
            if (limited) {
                activityStockService.rollback(request.getActivityId());
            }
            throw new BusinessException(4203, "活动报名人数已满");
        }

        ActivityEnroll enrollment = ActivityEnroll.builder()
                .userId(request.getUserId())
                .activityId(request.getActivityId())
                .enrollStatus(EnrollmentStatusEnum.ENROLLED.getCode())
                .build();
        try {
            activityEnrollRepository.insert(enrollment);
        } catch (DuplicateKeyException exception) {
            // 唯一索引 uk_activity_enroll_user (user_id, activity_id, delete_flag) 兜底。
            // 上面的 selectCount 检查有并发窗口，真正拦住重复报名的是这个索引。
            // 不转换的话它会一路冒泡到 GlobalExceptionHandler 的兜底分支，变成 5000 + 一长串 SQL 报错。
            //
            // 这里【要】手工回补 Redis：Spring 的 @Transactional 会自动回滚那条 UPDATE，
            // 但 Redis 不是事务资源，事务管理器管不到它。少了这一句就会出现"名额被白扣"，
            // 表现为活动明明还有空位却一直提示人数已满。
            if (limited) {
                activityStockService.rollback(request.getActivityId());
            }
            throw new BusinessException(4204, "请勿重复报名");
        }

        // 报名成功 → 三处缓存/统计要跟着动：
        //   1. 活动详情里的 currentParticipant 变了，详情缓存必须失效；
        //   2. 热门榜分数 +1；
        //   3. 库存 key 不用管，上面 tryDeduct 已经扣过了。
        activityCacheService.evict(request.getActivityId());
        hotActivityService.incrementEnroll(request.getActivityId());

        // 重新查一次，让返回体里的 currentParticipant 是占位后的真实值
        return toEnrollmentResponse(enrollment, activityMainRepository.selectById(request.getActivityId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse cancelEnrollment(Long enrollId, String userId) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException(4205, "仅可退出自己的活动报名记录");
        }

        ActivityMain activityMain = requireActivity(enrollment.getActivityId());
        if (!EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())
                || enrollment.getSignInTime() != null) {
            throw new BusinessException(4206, "已完成的活动不能退出");
        }
        if (ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(4207, "已结束活动不能退出");
        }

        // 同样用原子 SQL，而不是 "读出来 -1 再 updateById"（那是同一个 check-then-act 陷阱，
        // 并发退出时会少减，且 updateById 会把整行实体字段覆盖回去）。
        activityEnrollRepository.deleteById(enrollId);
        activityMainRepository.releaseSeat(enrollment.getActivityId());

        // 数据库名额已释放，Redis 闸门也要同步还回去，否则名额会越用越少（Redis 说满了、数据库其实有空位）。
        if (activityStockService.isLimited(activityMain)) {
            activityStockService.rollback(enrollment.getActivityId());
        }
        // current_participant 变了 → 详情缓存失效；热门榜分数 -1
        activityCacheService.evict(enrollment.getActivityId());
        hotActivityService.decrementEnroll(enrollment.getActivityId());

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

        enrollment.setEnrollStatus(EnrollmentStatusEnum.COMPLETED.getCode());
        enrollment.setSignInTime(LocalDateTime.now());
        activityEnrollRepository.updateById(enrollment);
        generateCreditRecord(enrollment, activityMain);
        return toEnrollmentResponse(enrollment, activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentResponse feedback(Long enrollId, FeedbackEnrollmentRequest request) {
        ActivityEnroll enrollment = requireEnrollment(enrollId);
        if (!enrollment.getUserId().equals(request.getUserId())) {
            throw new BusinessException(4213, "仅可评价自己的活动记录");
        }
        if (enrollment.getSignInTime() == null
                && !EnrollmentStatusEnum.COMPLETED.getCode().equals(enrollment.getEnrollStatus())) {
            throw new BusinessException(4214, "请先完成签到后再评价活动");
        }

        enrollment.setFeedbackScore(request.getFeedbackScore());
        enrollment.setFeedbackContent(request.getFeedbackContent());
        activityEnrollRepository.updateById(enrollment);
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

    private void generateCreditRecord(ActivityEnroll enrollment, ActivityMain activityMain) {
        if (creditRecordRepository.selectCount(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, enrollment.getUserId())
                .eq(CreditRecord::getActivityId, enrollment.getActivityId())) > 0) {
            return;
        }
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
        boolean canCancel = EnrollmentStatusEnum.ENROLLED.getCode().equals(enrollment.getEnrollStatus())
                && enrollment.getSignInTime() == null
                && activityMain != null
                && !ActivityStatusEnum.FINISHED.getCode().equals(displayStatus);
        boolean canFeedback = enrollment.getSignInTime() != null
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
                .feedbackScore(enrollment.getFeedbackScore())
                .feedbackContent(enrollment.getFeedbackContent())
                .canSignIn(canSignIn)
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
