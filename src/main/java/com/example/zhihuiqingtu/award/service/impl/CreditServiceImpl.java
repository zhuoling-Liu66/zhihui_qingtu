package com.example.zhihuiqingtu.award.service.impl;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.award.dto.CreditQueryRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.award.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.award.service.CreditService;
import com.example.zhihuiqingtu.award.vo.CreditRecordResponse;
import com.example.zhihuiqingtu.award.vo.CreditStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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
        //   - 管理员：审核队列要看全部记录，并允许按学号筛选用户；
        //   - 普通用户：只能看“自己获得的学分”，以及“自己发布过的第二课堂活动”产生的记录。
        //     原来只要不传 userId 就能拿到全表、传别人的学号就能精确查别人（水平越权 / IDOR）。
        //
        // 这里用“活动归属”而不是当前 publisher_status 判断发布者范围：
        // 管理员收回发布资格后，历史活动仍然存在，canReviewCredit 也仍允许活动发布者审核
        // 该活动产生的学分；若用当前资格判断，就会出现“能审却看不到”的不一致。
        if (currentUser.hasRole(UserRoleEnum.ADMIN.getCode())) {
            queryWrapper.eq(StringUtils.hasText(request.getUserId()), CreditRecord::getUserId, request.getUserId());
        } else {
            String currentUserId = currentUser.requireUserId();
            List<Long> ownActivityIds = findOwnSecondClassActivityIds(currentUserId);
            if (ownActivityIds.isEmpty()) {
                // 从未发布过第二课堂活动：忽略请求里的 userId，强制只看自己的记录。
                queryWrapper.eq(CreditRecord::getUserId, currentUserId);
            } else {
                queryWrapper.and(wrapper -> wrapper
                        .eq(CreditRecord::getUserId, currentUserId)
                        .or()
                        .in(CreditRecord::getActivityId, ownActivityIds));
                // 在自己可审范围内，仍允许按学号筛选（例如发布者查看某位学生的学分）。
                queryWrapper.eq(StringUtils.hasText(request.getUserId()),
                        CreditRecord::getUserId, request.getUserId());
            }
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
    public CreditStatisticsResponse getStatistics(String userId) {
        // 目标用户按角色决定，而不是直接采信请求参数（原来传别人的学号就能查别人的学分统计）：
        //   - 管理员：传了 userId 就统计该用户，不传则统计自己；
        //   - 普通用户：忽略传入的 userId，永远统计当前登录用户自己。
        String targetUserId = currentUser.hasRole(UserRoleEnum.ADMIN.getCode())
                ? (StringUtils.hasText(userId) ? userId : currentUser.requireUserId())
                : currentUser.requireUserId();
        // 保留原来的"仅校验用户存在"逻辑：换成服务端判定的目标用户后，它校验的是真正被统计的人
        // （目标账号已删除时会在这里得到 4009，而不是返回一份空统计）。
        requireUser(targetUserId);
        List<CreditRecord> records = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, targetUserId)
                .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.APPROVED.getCode()));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalVolunteerHours = BigDecimal.ZERO;
        Map<Integer, BigDecimal> creditMap = new HashMap<>();
        for (CreditRecord record : records) {
            BigDecimal credit = record.getCredit() == null ? BigDecimal.ZERO : record.getCredit();
            total = total.add(credit);
            // 志愿活动的发放记录 credit=0、creditType 为空，不参与学分分类统计。
            if (record.getCreditType() != null && credit.compareTo(BigDecimal.ZERO) > 0) {
                creditMap.merge(record.getCreditType(), credit, BigDecimal::add);
            }
            if (record.getVolunteerHours() != null) {
                totalVolunteerHours = totalVolunteerHours.add(record.getVolunteerHours());
            }
        }
        return CreditStatisticsResponse.builder()
                .userId(targetUserId)
                .totalCredit(total)
                .totalVolunteerHours(totalVolunteerHours)
                .innovationEntrepreneurshipCredit(creditMap.getOrDefault(CreditTypeEnum.INNOVATION_ENTREPRENEURSHIP.getCode(), BigDecimal.ZERO))
                .intellectualEducationCredit(creditMap.getOrDefault(CreditTypeEnum.INTELLECTUAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .moralEducationCredit(creditMap.getOrDefault(CreditTypeEnum.MORAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .physicalEducationCredit(creditMap.getOrDefault(CreditTypeEnum.PHYSICAL_EDUCATION.getCode(), BigDecimal.ZERO))
                .aestheticEducationCredit(creditMap.getOrDefault(CreditTypeEnum.AESTHETIC_EDUCATION.getCode(), BigDecimal.ZERO))
                .laborEducationCredit(creditMap.getOrDefault(CreditTypeEnum.LABOR_EDUCATION.getCode(), BigDecimal.ZERO))
                .build();
    }

    /**
     * 查询用户自己发布过的第二课堂活动 ID。
     *
     * <p>学分记录只由第二课堂活动产生，因此这里限定类型；活动归属是历史事实，
     * 不随发布资格被收回而改变。
     *
     * @param userId 当前用户学号
     * @return 活动 ID 列表；从未发布过时返回空列表
     */
    private List<Long> findOwnSecondClassActivityIds(String userId) {
        return activityMainRepository.selectList(new LambdaQueryWrapper<ActivityMain>()
                        .select(ActivityMain::getActivityId)
                        .eq(ActivityMain::getPublisherUserId, userId)
                        .eq(ActivityMain::getActivityType, ActivityTypeEnum.SECOND_CLASS.getCode()))
                .stream()
                .map(ActivityMain::getActivityId)
                .toList();
    }

    private CreditRecordResponse toResponse(CreditRecord creditRecord) {
        return CreditRecordResponse.builder()
                .recordId(creditRecord.getRecordId())
                .userId(creditRecord.getUserId())
                .activityId(creditRecord.getActivityId())
                .credit(creditRecord.getCredit())
                .creditType(creditRecord.getCreditType())
                .volunteerHours(creditRecord.getVolunteerHours())
                .verifyStatus(creditRecord.getVerifyStatus())
                .rejectReason(creditRecord.getRejectReason())
                .verifyTime(creditRecord.getVerifyTime())
                .createTime(creditRecord.getCreateTime())
                .build();
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return userInfo;
    }
}
