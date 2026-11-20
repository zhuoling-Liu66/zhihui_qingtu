package com.example.zhihuiqingtu.user.service.impl;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.user.dto.UpdateInterestTagsRequest;
import com.example.zhihuiqingtu.user.dto.UpdateUserProfileRequest;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.activity.entity.ActivityTag;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.entity.UserInterestRel;
import com.example.zhihuiqingtu.award.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.user.repository.UserInterestRelRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.user.service.UserService;
import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import com.example.zhihuiqingtu.user.vo.UserActivityHistoryVO;
import com.example.zhihuiqingtu.user.vo.UserCreditOverviewResponse;
import com.example.zhihuiqingtu.user.vo.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.zhihuiqingtu.exception.GlobalExceptionHandler;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;

/**
 * 用户服务实现。
 * <p>
 * <b>安全迁移要点：</b>本类方法的 {@code userId} 参数有两种语义，处理方式不同：
 * <ul>
 *   <li>{@link #getUserProfile(String)} —— "查询目标"（公开资料），保留且不做归属校验；</li>
 *   <li>{@link #updateUserProfile(String, UpdateUserProfileRequest)}、
 *       {@link #updateInterestTags(String, UpdateInterestTagsRequest)} —— "身份/归属"，
 *       <b>只允许操作自己</b>，管理员也不行（改资料不是管理动作，没有"代改"的业务理由）；</li>
 *   <li>{@link #getActivityHistories(String)}、{@link #getCreditOverview(String)} —— "查询目标"，
 *       但属个人数据：管理员/老师可查他人，普通用户只能查自己（见 {@link #checkReadableUser}）。</li>
 * </ul>
 * 这几个方法都保留 {@code userId} 签名（路径与 {@code /api/users/{userId}} 共用前缀，
 * 删掉路径段会产生歧义路由），但"能查谁/能改谁"一律由服务端按角色判定，
 * 不再直接采信客户端传来的学号。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserInfoRepository userInfoRepository;
    private final UserInterestRelRepository userInterestRelRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityMainRepository activityMainRepository;
    private final CreditRecordRepository creditRecordRepository;
    /**
     * 当前登录用户读取入口（基于 Spring Security 的 SecurityContextHolder）。
     * <p>
     * 用它来判定"这个请求到底是谁发的"：原来 {@code /api/users/{userId}/...}
     * 里的 {@code userId} 完全由客户端决定，填别人的学号就能改别人资料、看别人报名历史和学分。
     */
    private final CurrentUser currentUser;

    @Override
    public UserProfileResponse getUserProfile(String userId) {
        // 保留 userId 参数：查看任意用户的公开资料是校园场景的合理需求（例如看发布者的公开资料）。
        // 这里不做归属校验 —— 但要注意返回体里含手机号/邮箱等敏感字段，属于待整改项（见类注释/报告）。
        return buildUserProfile(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateUserProfile(String userId, UpdateUserProfileRequest request) {
        // ★ 越权点修复：原来直接 requireUser(userId) 并写库，客户端把路径里的 userId
        // 换成别人的学号就能改别人的资料（手机号、邮箱、专业等）。
        // 现在先做归属校验：只允许操作自己。
        // 选择"方法内显式校验"而不是 @PreAuthorize("#userId == authentication.name")：
        //   1) 校验失败能抛出项目统一的 BusinessException(4030)，由 GlobalExceptionHandler
        //      返回和其他业务错误一致的结构；@PreAuthorize 抛的是 AccessDeniedException，
        //      而本项目 GlobalExceptionHandler 没有它的处理器，会被兜底成 5000 "系统内部异常"；
        //   2) SpEL 依赖参数名解析（需要编译保留参数名），显式代码不依赖编译配置，审计时也更直观。
        String currentUserId = currentUser.requireUserId();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己的资料");
        }
        // 注意：先校验归属再查库，避免通过"用户不存在"与"无权限"两种不同错误码探测他人学号是否存在。
        UserInfo userInfo = requireUser(userId);
        validatePhoneAndEmail(userInfo, request);
        userInfo.setUserName(request.getUserName());
        userInfo.setUserPhone(request.getUserPhone());
        userInfo.setUserEmail(request.getUserEmail());
        userInfo.setMajor(request.getMajor());
        userInfo.setGrade(request.getGrade());
        userInfoRepository.updateById(userInfo);
        // 这里返回的手机号/邮箱不会被 buildUserProfile 屏蔽：
        // 本方法上面已经强制"只能改自己"，所以走到这里的必然是本人，
        // 返回值里带 phone/email 是"改完让我确认一下"的正常需求，不是泄露。
        return buildUserProfile(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateInterestTags(String userId, UpdateInterestTagsRequest request) {
        // ★ 越权点修复：兴趣标签是个人数据，原来客户端改路径里的 userId 就能改别人的标签
        // （先删光对方的 user_interest_rel 再写入自己的标签）。现在只允许改自己的。
        String currentUserId = currentUser.requireUserId();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己的兴趣标签");
        }
        UserInfo userInfo = requireUser(userId);
        List<Long> tagIds = new ArrayList<>(new LinkedHashSet<>(request.getTagIds()));
        validateInterestTags(tagIds);

        List<UserInterestRel> oldRelations = userInterestRelRepository.selectList(new LambdaQueryWrapper<UserInterestRel>()
                .eq(UserInterestRel::getUserId, userId));
        for (UserInterestRel relation : oldRelations) {
            userInterestRelRepository.deleteById(relation.getRelId());
        }

        for (Long tagId : tagIds) {
            userInterestRelRepository.insert(UserInterestRel.builder()
                    .userId(userId)
                    .tagId(tagId)
                    .build());
        }
        return buildUserProfile(userInfo);
    }

    @Override
    public List<UserActivityHistoryVO> getActivityHistories(String userId) {
        // ★ 越权点修复：报名历史含"谁报名了哪些活动"，属个人数据。
        // 可见范围按角色分支：管理员/老师可以查别人（便于管理、核对），普通用户只能查自己。
        // 原来这里是"谁传了 userId 就查谁"，等于任何登录用户都能翻别人的报名记录。
        checkReadableUser(userId, "只能查看自己的报名历史");
        requireUser(userId);
        List<ActivityEnroll> enrollments = activityEnrollRepository.selectList(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, userId)
                .orderByDesc(ActivityEnroll::getCreateTime));
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        var activityIds = enrollments.stream().map(ActivityEnroll::getActivityId).collect(Collectors.toSet());
        Map<Long, ActivityMain> activityMap = activityMainRepository.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, Function.identity()));

        return enrollments.stream()
                .map(enrollment -> {
                    ActivityMain activity = activityMap.get(enrollment.getActivityId());
                    return UserActivityHistoryVO.builder()
                            .enrollId(enrollment.getEnrollId())
                            .activityId(enrollment.getActivityId())
                            .activityTitle(activity == null ? null : activity.getActivityTitle())
                            .activityType(activity == null ? null : activity.getActivityType())
                            .enrollStatus(enrollment.getEnrollStatus())
                            .startTime(activity == null ? null : activity.getStartTime())
                            .endTime(activity == null ? null : activity.getEndTime())
                            .build();
                })
                .toList();
    }

    @Override
    public UserCreditOverviewResponse getCreditOverview(String userId) {
        // ★ 越权点修复：学分属个人数据。可见范围按角色分支：
        // 管理员/老师可查别人，普通用户只能查自己（原来传别人的学号就能拿到别人的学分总览）。
        checkReadableUser(userId, "只能查看自己的学分总览");
        requireUser(userId);
        List<CreditRecord> creditRecords = creditRecordRepository.selectList(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getUserId, userId)
                .eq(CreditRecord::getVerifyStatus, ReviewStatusEnum.APPROVED.getCode()));
        Map<Integer, BigDecimal> creditMap = new HashMap<>();
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalVolunteerHours = BigDecimal.ZERO;
        for (CreditRecord creditRecord : creditRecords) {
            BigDecimal credit = creditRecord.getCredit() == null ? BigDecimal.ZERO : creditRecord.getCredit();
            totalCredit = totalCredit.add(credit);
            // 志愿活动的发放记录 credit=0、creditType 为空，不参与学分分类统计。
            if (creditRecord.getCreditType() != null && credit.compareTo(BigDecimal.ZERO) > 0) {
                creditMap.merge(creditRecord.getCreditType(), credit, BigDecimal::add);
            }
            if (creditRecord.getVolunteerHours() != null) {
                totalVolunteerHours = totalVolunteerHours.add(creditRecord.getVolunteerHours());
            }
        }
        return UserCreditOverviewResponse.builder()
                .userId(userId)
                .totalCredit(totalCredit)
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
     * 读取个人数据前的归属校验：管理员可读他人，其余角色（含老师）只能读自己。
     *
     * @param userId        请求要读的目标用户
     * @param errorMessage  越权时的业务提示
     */
    private void checkReadableUser(String userId, String errorMessage) {
        if (currentUser.hasRole(com.example.zhihuiqingtu.user.enums.UserRoleEnum.ADMIN.getCode())) {
            return;
        }
        // ★ 身份来自 Spring Security 上下文，而不是请求参数；两者不一致说明在拿别人的学号越权
        if (!currentUser.requireUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, errorMessage);
        }
    }

    private void validateInterestTags(List<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        if (activityTagRepository.selectBatchIds(tagIds).size() != tagIds.size()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "存在无效的兴趣标签");
        }
    }

    private UserProfileResponse buildUserProfile(UserInfo userInfo) {
        List<UserInterestRel> relations = userInterestRelRepository.selectList(new LambdaQueryWrapper<UserInterestRel>()
                .eq(UserInterestRel::getUserId, userInfo.getStudentNo()));
        List<Long> tagIds = relations.stream().map(UserInterestRel::getTagId).toList();
        Map<Long, ActivityTag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : activityTagRepository.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(ActivityTag::getTagId, Function.identity()));

        List<ActivityTagVO> interestTags = relations.stream()
                .map(relation -> tagMap.get(relation.getTagId()))
                .filter(tag -> tag != null)
                .map(tag -> ActivityTagVO.builder().tagId(tag.getTagId()).tagName(tag.getTagName()).build())
                .toList();

        // 手机号/邮箱按"查看者是谁"决定是否填充（PII 最小暴露）：
        //   - 本人：填充 —— 自己的联系方式自己当然要看得到；
        //   - 管理员：填充 —— 管理场景需要联系用户；
        //   - 其他普通用户：置 null —— 学号 -> 手机号/邮箱的关联一旦开放，
        //     任何人都能批量拉取全校联系方式（爬虫/骚扰/撞库/社工），这属于个人信息泄露。
        // 为什么姓名、专业、年级、角色、发布资格不屏蔽：它们是校园场景里的公开身份信息
        // （"这个活动是谁发布的、哪个专业"本身就是活动详情需要展示的内容），
        // 单独拿到它们不足以直接联系或冒充本人，所以保留；真正敏感的是"可直接触达本人"的联系方式。
        // 注意：身份取不到时按"非本人"处理（fail closed —— 宁可少给字段，也不泄露）。
        boolean selfOrPrivileged = currentUser.hasRole(com.example.zhihuiqingtu.user.enums.UserRoleEnum.ADMIN.getCode())
                || userInfo.getStudentNo().equals(currentUser.userId().orElse(null));

        return UserProfileResponse.builder()
                .userId(userInfo.getStudentNo())
                .studentNo(userInfo.getStudentNo())
                .userName(userInfo.getUserName())
                .userPhone(selfOrPrivileged ? userInfo.getUserPhone() : null)
                .userEmail(selfOrPrivileged ? userInfo.getUserEmail() : null)
                .userRole(userInfo.getUserRole())
                .publisherStatus(userInfo.getPublisherStatus())
                .major(userInfo.getMajor())
                .grade(userInfo.getGrade())
                .interestTags(interestTags)
                .build();
    }

    private void validatePhoneAndEmail(UserInfo userInfo, UpdateUserProfileRequest request) {
        if (request.getUserPhone() != null && !request.getUserPhone().isBlank()) {
            UserInfo existingPhoneUser = userInfoRepository.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUserPhone, request.getUserPhone())
                    .last("LIMIT 1"));
            if (existingPhoneUser != null && !existingPhoneUser.getStudentNo().equals(userInfo.getStudentNo())) {
                throw new BusinessException(ErrorCode.CONFLICT, "手机号已被其他用户使用");
            }
        }
        if (request.getUserEmail() != null && !request.getUserEmail().isBlank()) {
            UserInfo existingEmailUser = userInfoRepository.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUserEmail, request.getUserEmail())
                    .last("LIMIT 1"));
            if (existingEmailUser != null && !existingEmailUser.getStudentNo().equals(userInfo.getStudentNo())) {
                throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被其他用户使用");
            }
        }
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return userInfo;
    }
}
