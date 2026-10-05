package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.credit.CreditQueryRequest;
import com.example.zhihuiqingtu.dto.credit.ReviewCreditRequest;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.service.CreditService;
import com.example.zhihuiqingtu.vo.credit.CreditRecordResponse;
import com.example.zhihuiqingtu.vo.credit.CreditStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学分服务实现。
 */
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final ActivityMainRepository activityMainRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final UserInfoRepository userInfoRepository;
    /**
     * 当前登录用户读取入口（基于 Spring Security 的 SecurityContextHolder）。
     * <p>
     * 用它替代原来从请求体/请求参数读 {@code verifyUserId}、{@code userId} ——
     * 那些字段是客户端自报的，任何人都能填别人的学号来伪造审核人或查别人的学分。
     */
    private final CurrentUser currentUser;

    @Override
    public PageResponse<CreditRecordResponse> queryCredits(CreditQueryRequest request) {
        LambdaQueryWrapper<CreditRecord> queryWrapper = new LambdaQueryWrapper<CreditRecord>()
                .eq(request.getVerifyStatus() != null, CreditRecord::getVerifyStatus, request.getVerifyStatus())
                .eq(request.getCreditType() != null, CreditRecord::getCreditType, request.getCreditType())
                .eq(request.getActivityId() != null, CreditRecord::getActivityId, request.getActivityId())
                .orderByDesc(CreditRecord::getCreateTime);

        // 可见范围按角色分支，不由客户端自报的 userId 决定：
        //   - 管理员/老师：审核队列要看全部记录，并允许按学号筛选学生（老师按学号找人）；
        //   - 学生发布者：只能看【自己发布的活动】产生的记录（见下面分支的详细说明）；
        //   - 普通学生：强制只看自己 —— 原来这里是 .eq(request.getUserId() != null, ...)
        //     的"可选"条件，不传就能拿到全表、传别人的学号就能精确查别人（水平越权 / IDOR）。
        if (currentUser.isAdminOrTeacher()) {
            queryWrapper.eq(StringUtils.hasText(request.getUserId()), CreditRecord::getUserId, request.getUserId());
        } else if (isPublisher()) {
            // 学生发布者分支：只能看到"自己发布的活动"产生的学分记录。
            // <p>
            // 为什么发布者必须能看到别人的记录：学分记录的 userId 是<b>获得学分的学生</b>，
            // 不是活动发布者。而 canReviewCredit 本来就允许第二课堂活动的发布者审核该活动
            // 产生的学分 —— 如果这里强制"只看自己"，就会出现"能审却看不到"的功能退化
            // （审核队列永远是空的，审核入口形同虚设）。
            // <p>
            // 为什么这仍然比原来更安全：原来只要不传 userId 就能拿到全表（全站学生的学分）；
            // 现在范围由数据库里的归属关系（activity_main.publisher_user_id = 当前用户）决定，
            // 客户端既不能扩大范围也不能伪造 —— 最多只覆盖自己发布过的活动。
            String currentUserId = currentUser.requireUserId();
            List<Long> ownActivityIds = activityMainRepository.selectList(new LambdaQueryWrapper<ActivityMain>()
                            .select(ActivityMain::getActivityId)
                            .eq(ActivityMain::getPublisherUserId, currentUserId)
                            // 只有第二课堂活动才产生学分记录，限定类型让"自己发布的活动"更精确
                            .eq(ActivityMain::getActivityType, ActivityTypeEnum.SECOND_CLASS.getCode()))
                    .stream()
                    .map(ActivityMain::getActivityId)
                    .toList();
            if (ownActivityIds.isEmpty()) {
                // 没发布过第二课堂活动 = 没有可审核的学分记录。
                // 这是正常的"空结果"，不是错误，所以返回空分页而不是抛异常
                // （写法与 TeamServiceImpl.queryTeams 的 emptyPage 保持一致）。
                Page<CreditRecordResponse> emptyPage = new Page<>(request.getPageNo() + 1L, request.getPageSize(), 0);
                emptyPage.setRecords(Collections.emptyList());
                return PageResponse.from(emptyPage);
            }
            queryWrapper.in(CreditRecord::getActivityId, ownActivityIds)
                    // 发布者也可以在自己的活动范围内按学号筛选学生
                    .eq(StringUtils.hasText(request.getUserId()), CreditRecord::getUserId, request.getUserId());
        } else {
            // 普通学生：忽略请求里的 userId，强制只查自己
            queryWrapper.eq(CreditRecord::getUserId, currentUser.requireUserId());
        }

        Page<CreditRecord> page = creditRecordRepository.selectPage(
                new Page<>(request.getPageNo() + 1L, request.getPageSize()),
                queryWrapper
        );
        Page<CreditRecordResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditRecordResponse reviewCredit(Long recordId, ReviewCreditRequest request) {
        // ★ 审核人 = 当前登录用户（原来读 request.getVerifyUserId()，可被伪造成管理员/老师）
        String reviewerUserId = currentUser.requireUserId();
        UserInfo verifier = requireUser(reviewerUserId);
        if (!ReviewStatusEnum.APPROVED.getCode().equals(request.getVerifyStatus())
                && !ReviewStatusEnum.REJECTED.getCode().equals(request.getVerifyStatus())) {
            throw new BusinessException(4402, "审核状态仅支持通过或驳回");
        }

        CreditRecord creditRecord = requireCreditRecord(recordId);
        ActivityMain activityMain = requireActivity(creditRecord.getActivityId());
        // 权限判定逻辑本身不变（管理员/老师/活动发布者可审核），
        // 变的只是"审核人是谁"—— 现在来自认证上下文，伪造不了。
        if (!canReviewCredit(verifier, activityMain)) {
            throw new BusinessException(4401, "仅管理员、老师或活动发布者可审核该学分记录");
        }

        creditRecord.setVerifyUserId(reviewerUserId);
        creditRecord.setVerifyStatus(request.getVerifyStatus());
        creditRecord.setRejectReason(request.getRejectReason());
        creditRecord.setVerifyTime(LocalDateTime.now());
        creditRecordRepository.updateById(creditRecord);
        return toResponse(creditRecord);
    }

    @Override
    public CreditStatisticsResponse getStatistics(String userId) {
        // 目标用户按角色决定，而不是直接采信请求参数（原来传别人的学号就能查别人的学分统计）：
        //   - 管理员/老师：传了 userId 就统计该用户，不传则统计自己（前端"我的学分总览"就是不带参数调的）；
        //   - 普通用户：忽略传入的 userId，永远统计当前登录用户自己。
        String targetUserId = currentUser.isAdminOrTeacher()
                ? (StringUtils.hasText(userId) ? userId : currentUser.requireUserId())
                : currentUser.requireUserId();
        // 保留原来的"仅校验用户存在"逻辑：换成服务端判定的目标用户后，它校验的是真正被统计的人
        // （目标账号已删除时会在这里得到 4009，而不是返回一份空统计）。
        requireUser(targetUserId);
        List<CreditRecord> records = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, targetUserId)
                .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.APPROVED.getCode()));
        BigDecimal total = BigDecimal.ZERO;
        Map<Integer, BigDecimal> creditMap = new HashMap<>();
        for (CreditRecord record : records) {
            total = total.add(record.getCredit());
            creditMap.merge(record.getCreditType(), record.getCredit(), BigDecimal::add);
        }
        return CreditStatisticsResponse.builder()
                .userId(targetUserId)
                .totalCredit(total)
                .innovationEntrepreneurshipCredit(creditMap.getOrDefault(CreditTypeEnum.INNOVATION_ENTREPRENEURSHIP.getCode(), BigDecimal.ZERO))
                .intellectualEducationCredit(creditMap.getOrDefault(CreditTypeEnum.INTELLECTUAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .moralEducationCredit(creditMap.getOrDefault(CreditTypeEnum.MORAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .physicalEducationCredit(creditMap.getOrDefault(CreditTypeEnum.PHYSICAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .aestheticEducationCredit(creditMap.getOrDefault(CreditTypeEnum.AESTHETIC_EDUCATION.getCode(), BigDecimal.ZERO))
                .laborEducationCredit(creditMap.getOrDefault(CreditTypeEnum.LABOR_EDUCATION.getCode(), BigDecimal.ZERO))
                .build();
    }

    /**
     * 当前登录用户是否具备活动发布资格（{@code publisher_status = 1}）。
     * <p>
     * 为什么不从 JWT 判断：令牌里的 claim 只有 {@code jti/sub/role/iat/exp}，没有发布资格；
     * 而且发布资格是数据库里可变的授权状态（管理员可以随时开通/收回），
     * 把它编进令牌会带来"资格被收回后旧令牌仍能用"的时间窗。所以这里查库。
     *
     * @return true 表示当前登录用户已开通发布资格
     */
    private boolean isPublisher() {
        UserInfo userInfo = requireUser(currentUser.requireUserId());
        return PublisherStatusEnum.ENABLED.getCode().equals(userInfo.getPublisherStatus());
    }

    private CreditRecordResponse toResponse(CreditRecord creditRecord) {
        return CreditRecordResponse.builder()
                .recordId(creditRecord.getRecordId())
                .userId(creditRecord.getUserId())
                .activityId(creditRecord.getActivityId())
                .credit(creditRecord.getCredit())
                .creditType(creditRecord.getCreditType())
                .verifyStatus(creditRecord.getVerifyStatus())
                .rejectReason(creditRecord.getRejectReason())
                .verifyTime(creditRecord.getVerifyTime())
                .createTime(creditRecord.getCreateTime())
                .build();
    }

    private boolean canReviewCredit(UserInfo verifier, ActivityMain activityMain) {
        if (UserRoleEnum.ADMIN.getCode().equals(verifier.getUserRole())
                || UserRoleEnum.TEACHER.getCode().equals(verifier.getUserRole())) {
            return true;
        }
        return ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityMain.getActivityType())
                && verifier.getStudentNo().equals(activityMain.getPublisherUserId());
    }

    private CreditRecord requireCreditRecord(Long recordId) {
        CreditRecord creditRecord = creditRecordRepository.selectById(recordId);
        if (creditRecord == null) {
            throw new BusinessException(4400, "学分记录不存在");
        }
        return creditRecord;
    }

    private ActivityMain requireActivity(Long activityId) {
        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            throw new BusinessException(4100, "活动不存在");
        }
        return activityMain;
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(4009, "用户不存在");
        }
        return userInfo;
    }
}
