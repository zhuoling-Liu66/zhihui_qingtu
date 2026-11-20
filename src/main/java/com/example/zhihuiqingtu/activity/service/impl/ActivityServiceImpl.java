package com.example.zhihuiqingtu.activity.service.impl;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.TransactionEvents;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.config.UploadProperties;
import com.example.zhihuiqingtu.activity.dto.ActivityQueryRequest;
import com.example.zhihuiqingtu.activity.dto.ChangeActivitySignInRequest;
import com.example.zhihuiqingtu.activity.dto.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.activity.dto.CreateActivityRequest;
import com.example.zhihuiqingtu.activity.dto.CreditTierRequest;
import com.example.zhihuiqingtu.activity.dto.UpdateActivityRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityCreditTier;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import com.example.zhihuiqingtu.activity.entity.ActivityTag;
import com.example.zhihuiqingtu.activity.entity.ActivityTagRel;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.team.entity.TeamMain;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.activity.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.activity.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.award.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.team.repository.TeamMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.activity.service.ActivityQueryService;
import com.example.zhihuiqingtu.activity.service.ActivityService;
import com.example.zhihuiqingtu.activity.support.ActivityAssembler;
import com.example.zhihuiqingtu.activity.vo.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityStockResponse;
import com.example.zhihuiqingtu.activity.vo.ActivityTagVO;
import com.example.zhihuiqingtu.activity.vo.CreditTierVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.support.ActivityStockService;
import com.example.zhihuiqingtu.activity.support.HotActivityService;
import com.example.zhihuiqingtu.team.entity.TeamMember;

/**
 * 活动服务实现。
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final ActivityQueryService activityQueryService;
    private final ActivityAssembler activityAssembler;
    private final ActivityMainRepository activityMainRepository;
    private final ActivityCreditTierRepository activityCreditTierRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityTagRelRepository activityTagRelRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final TeamMainRepository teamMainRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserInfoRepository userInfoRepository;
    private final UploadProperties uploadProperties;
    /** 活动详情缓存（Redis）。所有写路径结束后都要让它失效。 */
    private final ActivityCacheService activityCacheService;
    /** 活动名额库存闸门（Redis）。活动变更后要按数据库权威值重置。 */
    private final ActivityStockService activityStockService;
    /** 热门活动榜（Redis ZSet）。 */
    private final HotActivityService hotActivityService;
    /**
     * 当前登录用户读取入口（基于 Spring Security 的 SecurityContextHolder）。
     * <p>
     * 用它替代原来从请求体读 {@code publisherUserId} / {@code operatorUserId} ——
     * 那些字段是客户端自报的，任何人都能填别人的学号来越权。
     */
    private final CurrentUser currentUser;

    @Override
    public ActivityCoverUploadResponse uploadActivityCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先选择活动封面图片");
        }
        if (file.getSize() > uploadProperties.getMaxCoverSize().toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "活动封面不能超过5MB");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持上传图片格式的活动封面");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        Path coverDir = Paths.get(uploadProperties.getActivityCoverDir()).toAbsolutePath().normalize();
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = coverDir.resolve(fileName).normalize();

        try {
            Files.createDirectories(coverDir);
            if (!target.startsWith(coverDir)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "封面保存路径非法");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动封面上传失败，请稍后重试");
        }

        return ActivityCoverUploadResponse.builder()
                .coverUrl("/uploads/activity-covers/" + fileName)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse createActivity(CreateActivityRequest request) {
        // ★ 发布者 = 当前登录用户，不再读 request.getPublisherUserId()
        String publisherId = currentUser.requireUserId();
        UserInfo publisher = requireUser(publisherId);
        checkClientPublishPermission(publisher);
        validateNonCreditActivity(request);
        return createActivityInternal(request, publisherId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse createStaffActivity(CreateActivityRequest request) {
        // 管理端运营账号负责学分/志愿活动；学生与老师只能在客户端发布无学分无时长的活动。
        String publisherId = currentUser.requireUserId();
        UserInfo publisher = requireUser(publisherId);
        if (!UserRoleEnum.STAFF.getCode().equals(publisher.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理端运营人员账号可以发布活动");
        }
        requireStaffPublishableType(request.getActivityType());
        return createActivityInternal(request, publisherId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse createAdminActivity(CreateActivityRequest request) {
        // 管理员发布免审核：管理员本身就是审核人，再排队审核自己发起的活动没有意义。
        String publisherId = currentUser.requireUserId();
        UserInfo publisher = requireUser(publisherId);
        if (!UserRoleEnum.ADMIN.getCode().equals(publisher.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可以在管理端直接发布活动");
        }
        requireValidActivityType(request.getActivityType());
        return createActivityInternal(request, publisherId, true);
    }

    private void requireValidActivityType(Integer activityType) {
        for (ActivityTypeEnum value : ActivityTypeEnum.values()) {
            if (value.getCode().equals(activityType)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "活动类型不合法");
    }

    private void requireStaffPublishableType(Integer activityType) {
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)
                && !ActivityTypeEnum.VOLUNTEER.getCode().equals(activityType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "运营人员只能发布学分活动或志愿活动");
        }
    }

    private ActivityResponse createActivityInternal(CreateActivityRequest request, String publisherId,
                                                    boolean autoApproved) {
        validateActivityTime(request.getStartTime(), request.getEndTime());
        validateTags(request.getTagIds());
        Integer creditMode = normalizeCreditMode(request.getActivityType(), request.getCreditMode());
        Integer volunteerMode = normalizeVolunteerMode(request.getActivityType(), request.getVolunteerMode());
        validateActivityAttributes(
                request.getActivityType(),
                request.getActivityCredit(),
                request.getCreditType(),
                request.getVolunteerHours(),
                creditMode,
                volunteerMode,
                request.getCreditTiers()
        );

        ActivityMain activityMain = ActivityMain.builder()
                .publisherUserId(publisherId)
                .activityTitle(request.getActivityTitle())
                .activityDesc(request.getActivityDesc())
                .activityType(request.getActivityType())
                .activityCredit(resolveStoredActivityCredit(
                        request.getActivityType(), creditMode, request.getActivityCredit(), request.getCreditTiers()))
                .creditType(normalizeCreditType(request.getActivityType(), request.getCreditType()))
                .creditMode(creditMode)
                .volunteerHours(normalizeVolunteerHours(request.getActivityType(), request.getVolunteerHours()))
                .volunteerMode(volunteerMode)
                .coverUrl(normalizeCoverUrl(request.getCoverUrl()))
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .activityLocation(request.getActivityLocation())
                .maxParticipant(request.getMaxParticipant())
                .currentParticipant(0)
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .signInEnabled(0)
                // ★ 普通发布进入待审核；管理员发布（autoApproved）直接视为已通过。
                .reviewStatus(autoApproved
                        ? ReviewStatusEnum.APPROVED.getCode()
                        : ReviewStatusEnum.PENDING.getCode())
                .reviewerUserId(autoApproved ? publisherId : null)
                .reviewTime(autoApproved ? LocalDateTime.now() : null)
                .build();
        activityMainRepository.insert(activityMain);
        syncActivityTags(activityMain.getActivityId(), request.getTagIds());
        syncCreditTiers(activityMain.getActivityId(), creditMode, request.getCreditTiers());
        // 初始化 Redis 库存（此时 currentParticipant=0，remaining 就是 maxParticipant）。
        // 放到提交后执行：事务若回滚，不应该在 Redis 里留下一个不存在活动的库存 key。
        Long createdActivityId = activityMain.getActivityId();
        Integer createdMaxParticipant = activityMain.getMaxParticipant();
        TransactionEvents.afterCommit("创建活动后初始化库存 activityId=" + createdActivityId,
                () -> activityStockService.reset(createdActivityId, createdMaxParticipant, 0));
        // 新建的活动不可能已经在缓存里，所以这里【不需要】evict，这是梳理写路径时容易多写的一步。
        // 直接由插入后的实体构造返回值，不经过详情方法（避免把未提交的数据写进缓存）。
        return activityAssembler.toResponseWithTiers(activityMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse updateActivity(Long activityId, UpdateActivityRequest request) {
        validateNonCreditActivity(request);
        return updateActivityInternal(activityId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse updateStaffActivity(Long activityId, UpdateActivityRequest request) {
        UserInfo operator = requireUser(currentUser.requireUserId());
        if (!UserRoleEnum.STAFF.getCode().equals(operator.getUserRole())
                && !UserRoleEnum.ADMIN.getCode().equals(operator.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理端账号可以编辑活动");
        }
        ActivityMain current = requireActivity(activityId);
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(current.getActivityType())
                && !ActivityTypeEnum.VOLUNTEER.getCode().equals(current.getActivityType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该活动不是学分或志愿活动");
        }
        requireStaffPublishableType(request.getActivityType());
        return updateActivityInternal(activityId, request);
    }

    private ActivityResponse updateActivityInternal(Long activityId, UpdateActivityRequest request) {
        ActivityMain activityMain = requireActivity(activityId);
        // ★ 操作者 = 当前登录用户（原来读 request.getOperatorUserId()）
        UserInfo operator = requireUser(currentUser.requireUserId());
        checkActivityManagePermission(activityMain, operator);
        validateActivityTime(request.getStartTime(), request.getEndTime());
        validateTags(request.getTagIds());
        Integer creditMode = normalizeCreditMode(request.getActivityType(), request.getCreditMode());
        Integer volunteerMode = normalizeVolunteerMode(request.getActivityType(), request.getVolunteerMode());
        validateActivityAttributes(
                request.getActivityType(),
                request.getActivityCredit(),
                request.getCreditType(),
                request.getVolunteerHours(),
                creditMode,
                volunteerMode,
                request.getCreditTiers()
        );

        // 0 表示不限人数，不能参与"小于当前报名人数"的比较，否则 0 会被误判为超限。
        if (request.getMaxParticipant() > 0
                && request.getMaxParticipant() < activityMain.getCurrentParticipant()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "最大参与人数不能小于当前已报名人数");
        }

        activityMain.setActivityTitle(request.getActivityTitle());
        // 描述/地点/封面没有 @NotNull：未传时保留原值，避免"某次编辑没带这个字段就被清空"。
        activityMain.setActivityDesc(request.getActivityDesc() == null
                ? activityMain.getActivityDesc()
                : request.getActivityDesc());
        activityMain.setActivityType(request.getActivityType());
        activityMain.setActivityCredit(resolveStoredActivityCredit(
                request.getActivityType(), creditMode, request.getActivityCredit(), request.getCreditTiers()));
        activityMain.setCreditType(normalizeCreditType(request.getActivityType(), request.getCreditType()));
        activityMain.setCreditMode(creditMode);
        activityMain.setVolunteerHours(normalizeVolunteerHours(request.getActivityType(), request.getVolunteerHours()));
        activityMain.setVolunteerMode(volunteerMode);
        activityMain.setCoverUrl(StringUtils.hasText(request.getCoverUrl())
                ? normalizeCoverUrl(request.getCoverUrl())
                : activityMain.getCoverUrl());
        activityMain.setStartTime(request.getStartTime());
        activityMain.setEndTime(request.getEndTime());
        activityMain.setActivityLocation(request.getActivityLocation() == null
                ? activityMain.getActivityLocation()
                : request.getActivityLocation());
        activityMain.setMaxParticipant(request.getMaxParticipant());

        // 只更新可编辑字段，并且把容量校验并入同一条 UPDATE 的 WHERE：
        // 校验通过之后如果又有人报名，UPDATE 会重新求值，不会把最大人数写到小于实际报名数。
        // 也不会再把读出来的旧 current_participant 一起写回（那是丢失更新的根源）。
        int updated = activityMainRepository.updateEditableFields(activityMain);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "最大参与人数不能小于当前已报名人数，请刷新后重试");
        }
        syncActivityTags(activityId, request.getTagIds());
        syncCreditTiers(activityId, creditMode, request.getCreditTiers());

        // 提交后再动 Redis：
        //   1. 按提交后的权威值重算库存（不能用内存里的旧 currentParticipant，并发报名后它会偏小）；
        //   2. 详情缓存失效。
        TransactionEvents.afterCommit("编辑活动后重置库存与失效缓存 activityId=" + activityId, () -> {
            ActivityMain latest = activityMainRepository.selectById(activityId);
            if (latest != null) {
                activityStockService.reset(activityId, latest.getMaxParticipant(), latest.getCurrentParticipant());
            }
            activityCacheService.evict(activityId);
        });

        // 写接口不经过 getActivityDetail：那是带缓存的读路径，可能把旧缓存原样返回。
        // 这里直接读库构造，保证返回值就是刚提交的数据。
        return activityAssembler.toResponseWithTiers(requireActivity(activityId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse changeActivityStatus(Long activityId, ChangeActivityStatusRequest request) {
        ActivityMain activityMain = requireActivity(activityId);
        // ★ 操作者 = 当前登录用户，不再从请求体读 operatorUserId（客户端自报，可伪造）
        UserInfo operator = requireUser(currentUser.requireUserId());
        checkActivityManagePermission(activityMain, operator);

        if (!ActivityStatusEnum.isPersistentStatus(request.getActivityStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动状态仅支持切换为已发布或已取消");
        }
        // 审核通过前不得重新发布到公开列表。已通过的活动被修改后也会回到待审核。
        if (ActivityStatusEnum.PUBLISHED.getCode().equals(request.getActivityStatus())
                && !ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动尚未通过审核，不能发布");
        }
        // 只更新状态这一列：不再整行写回，避免把 current_participant 等字段的旧值覆盖回去。
        int updated = activityMainRepository.updateStatus(activityId, request.getActivityStatus());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动状态已变化，请刷新后重试");
        }
        // 活动状态变了 → 缓存里的 displayStatus / activityStatus 已过期，提交后失效缓存
        TransactionEvents.afterCommit("活动状态变更后失效缓存 activityId=" + activityId,
                () -> activityCacheService.evict(activityId));
        return activityAssembler.toResponse(requireActivity(activityId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse updateSignInSwitch(Long activityId, ChangeActivitySignInRequest request) {
        ActivityMain activityMain = requireActivity(activityId);
        // ★ 操作者 = 当前登录用户
        UserInfo operator = requireUser(currentUser.requireUserId());
        checkActivityManagePermission(activityMain, operator);

        if (!Integer.valueOf(0).equals(request.getSignInEnabled()) && !Integer.valueOf(1).equals(request.getSignInEnabled())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "签到开关仅支持0或1");
        }
        if (Integer.valueOf(1).equals(request.getSignInEnabled())
                && !ReviewStatusEnum.APPROVED.getCode().equals(activityMain.getReviewStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动尚未通过审核，不能开启签到");
        }
        if (Integer.valueOf(1).equals(request.getSignInEnabled())
                && ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已结束活动不能再开启签到");
        }
        if (ActivityStatusEnum.CANCELED.getCode().equals(activityMain.getActivityStatus())
                && Integer.valueOf(1).equals(request.getSignInEnabled())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已取消活动不能开启签到");
        }

        // 只更新签到开关这一列，理由同上：避免整行写回覆盖并发报名刚加的人数。
        int updated = activityMainRepository.updateSignInEnabled(activityId, request.getSignInEnabled());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动状态已变化，请刷新后重试");
        }
        // sign_in_enabled 是 ActivityResponse 的一个字段，缓存必须失效 ——
        // 否则"刚开启签到但学生点签到仍提示未开放"（前端拿到的详情是缓存，会显示错误的按钮状态）。
        TransactionEvents.afterCommit("签到开关变更后失效缓存 activityId=" + activityId,
                () -> activityCacheService.evict(activityId));
        return activityAssembler.toResponse(requireActivity(activityId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long activityId) {
        ActivityMain activityMain = requireActivity(activityId);
        // ★ 操作者 = 当前登录用户（原来由 @RequestParam operatorUserId 传入，可伪造）
        UserInfo operator = requireUser(currentUser.requireUserId());
        checkActivityManagePermission(activityMain, operator);

        activityTagRelRepository.delete(new LambdaQueryWrapper<ActivityTagRel>()
                .eq(ActivityTagRel::getActivityId, activityId));
        activityCreditTierRepository.delete(new LambdaQueryWrapper<ActivityCreditTier>()
                .eq(ActivityCreditTier::getActivityId, activityId));
        activityEnrollRepository.delete(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getActivityId, activityId));
        creditRecordRepository.delete(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getActivityId, activityId));

        List<TeamMain> teams = teamMainRepository.selectList(new LambdaQueryWrapper<TeamMain>()
                .eq(TeamMain::getActivityId, activityId));
        List<Long> teamIds = teams.stream().map(TeamMain::getTeamId).toList();
        if (!teamIds.isEmpty()) {
            teamMemberRepository.delete(new LambdaQueryWrapper<com.example.zhihuiqingtu.team.entity.TeamMember>()
                    .in(com.example.zhihuiqingtu.team.entity.TeamMember::getTeamId, teamIds));
            teamMainRepository.delete(new LambdaQueryWrapper<TeamMain>()
                    .in(TeamMain::getTeamId, teamIds));
        }
        activityMainRepository.deleteById(activityId);
        // 级联删除结束后清掉 Redis 状态，但必须等数据库真正提交：
        //   - 详情缓存：不删的话，已删除的活动在 TTL 内还能被查到（前端会看到"删除没生效"）
        //   - 库存 key：不删的话会留下一个永远没人清理的无主 key
        //   - 热门榜：不删的话榜单里残留一个查不到的 id（读取时会被跳过，但没必要留着）
        // 若在提交前清理而事务最终回滚，活动仍然存在，榜单条目却已经被删且无法自动恢复。
        TransactionEvents.afterCommit("删除活动后清理缓存/库存/热门榜 activityId=" + activityId, () -> {
            activityCacheService.evict(activityId);
            activityStockService.evict(activityId);
            hotActivityService.remove(activityId);
        });
    }

    // ══════════════════ 只读查询（委托 ActivityQueryService）══════════════════

    @Override
    public PageResponse<ActivityResponse> queryActivities(ActivityQueryRequest request) {
        return activityQueryService.queryActivities(request);
    }

    @Override
    public ActivityResponse getActivityDetail(Long activityId) {
        return activityQueryService.getActivityDetail(activityId);
    }

    @Override
    public List<ActivityTagVO> listActivityTags() {
        return activityQueryService.listActivityTags();
    }

    @Override
    public List<ActivityResponse> listHotActivities(int limit) {
        return activityQueryService.listHotActivities(limit);
    }

    @Override
    public ActivityStockResponse getActivityStock(Long activityId) {
        return activityQueryService.getActivityStock(activityId);
    }

    private void syncActivityTags(Long activityId, List<Long> tagIds) {
        List<ActivityTagRel> oldRelations = activityTagRelRepository.selectList(
                new LambdaQueryWrapper<ActivityTagRel>().eq(ActivityTagRel::getActivityId, activityId)
        );
        for (ActivityTagRel relation : oldRelations) {
            activityTagRelRepository.deleteById(relation.getRelId());
        }
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        List<Long> finalTagIds = new ArrayList<>(new LinkedHashSet<>(tagIds));
        for (Long tagId : finalTagIds) {
            activityTagRelRepository.insert(ActivityTagRel.builder()
                    .activityId(activityId)
                    .tagId(tagId)
                    .build());
        }
    }

    private void validateTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueTagIds = new LinkedHashSet<>(tagIds);
        List<ActivityTag> tags = activityTagRepository.selectBatchIds(uniqueTagIds);
        if (tags.size() != uniqueTagIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在无效的活动标签，请先调用 /api/activities/tags 查看可用标签ID");
        }
    }

    private void validateActivityAttributes(Integer activityType, BigDecimal activityCredit, Integer creditType,
                                           BigDecimal volunteerHours, Integer creditMode, Integer volunteerMode,
                                           List<CreditTierRequest> creditTiers) {
        if (ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            if (creditType == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "第二课堂学分活动必须选择学分类型");
            }
            if (!isValidCreditType(creditType)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "学分类型不合法");
            }
            if (Integer.valueOf(2).equals(creditMode)) {
                // 组合学分：具体金额由档位定义，activity_credit 只存最大档用于展示
                validateCreditTiers(creditTiers);
            } else if (activityCredit == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "第二课堂学分活动必须设置学分");
            }
            if (volunteerHours != null && volunteerHours.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "第二课堂学分活动不能设置志愿服务时长");
            }
            return;
        }

        if (ActivityTypeEnum.VOLUNTEER.getCode().equals(activityType)) {
            if (Integer.valueOf(1).equals(volunteerMode) && volunteerHours == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "固定时长模式必须设置志愿服务时长");
            }
            if (activityCredit != null && activityCredit.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿服务活动不允许设置学分");
            }
            if (creditType != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿服务活动不允许设置学分类型");
            }
            if (creditTiers != null && !creditTiers.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿服务活动不能配置学分档位");
            }
            return;
        }

        if (activityCredit != null && activityCredit.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非第二课堂学分活动不允许设置学分");
        }
        if (creditType != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非第二课堂学分活动不允许设置学分类型");
        }
        if (volunteerHours != null && volunteerHours.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅志愿服务活动允许设置志愿服务时长");
        }
        if (creditTiers != null && !creditTiers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅组合学分活动允许配置学分档位");
        }
    }

    private void validateCreditTiers(List<CreditTierRequest> creditTiers) {
        if (creditTiers == null || creditTiers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "组合学分至少需要配置一个档位");
        }
        Set<BigDecimal> seen = new LinkedHashSet<>();
        for (CreditTierRequest tier : creditTiers) {
            if (tier == null || tier.getCredit() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "档位学分不能为空");
            }
            if (tier.getQuota() == null || tier.getQuota() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "档位名额必须大于0");
            }
            BigDecimal normalized = tier.getCredit().stripTrailingZeros();
            if (tier.getCredit().compareTo(BigDecimal.ZERO) <= 0
                    || normalized.scale() > 2
                    || normalized.remainder(new BigDecimal("0.05")).compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "档位学分必须大于0且以0.05为单位");
            }
            if (!seen.add(normalized)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "同一活动不能配置重复的档位学分");
            }
        }
    }

    private boolean isValidCreditType(Integer creditType) {
        for (CreditTypeEnum value : CreditTypeEnum.values()) {
            if (value.getCode().equals(creditType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 组合学分时 {@code activity_credit} 存最大档，用于列表展示；固定模式存用户填写的学分。
     */
    private BigDecimal resolveStoredActivityCredit(Integer activityType, Integer creditMode,
                                                   BigDecimal activityCredit, List<CreditTierRequest> creditTiers) {
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            return BigDecimal.ZERO;
        }
        if (Integer.valueOf(2).equals(creditMode)) {
            return creditTiers.stream()
                    .map(CreditTierRequest::getCredit)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }
        return activityCredit;
    }

    private Integer normalizeCreditMode(Integer activityType, Integer creditMode) {
        if (!ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            return null;
        }
        if (creditMode == null) {
            return 1;
        }
        if (!Integer.valueOf(1).equals(creditMode) && !Integer.valueOf(2).equals(creditMode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学分模式仅支持固定或组合");
        }
        return creditMode;
    }

    private Integer normalizeVolunteerMode(Integer activityType, Integer volunteerMode) {
        if (!ActivityTypeEnum.VOLUNTEER.getCode().equals(activityType)) {
            return null;
        }
        if (volunteerMode == null) {
            return 1;
        }
        if (!Integer.valueOf(1).equals(volunteerMode) && !Integer.valueOf(2).equals(volunteerMode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿时长模式仅支持固定或灵活");
        }
        return volunteerMode;
    }

    private Integer normalizeCreditType(Integer activityType, Integer creditType) {
        if (ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            return creditType;
        }
        return null;
    }

    private BigDecimal normalizeVolunteerHours(Integer activityType, BigDecimal volunteerHours) {
        if (ActivityTypeEnum.VOLUNTEER.getCode().equals(activityType)) {
            return volunteerHours;
        }
        return null;
    }

    /**
     * 组合学分档位全删全插；固定学分模式不保留档位。
     */
    private void syncCreditTiers(Long activityId, Integer creditMode, List<CreditTierRequest> creditTiers) {
        activityCreditTierRepository.delete(new LambdaQueryWrapper<ActivityCreditTier>()
                .eq(ActivityCreditTier::getActivityId, activityId));
        if (!Integer.valueOf(2).equals(creditMode) || creditTiers == null) {
            return;
        }
        int sortNo = 0;
        for (CreditTierRequest tier : creditTiers) {
            activityCreditTierRepository.insert(ActivityCreditTier.builder()
                    .activityId(activityId)
                    .credit(tier.getCredit())
                    .quota(tier.getQuota())
                    .sortNo(sortNo++)
                    .build());
        }
    }

    private String normalizeCoverUrl(String coverUrl) {
        return StringUtils.hasText(coverUrl) ? coverUrl.trim() : null;
    }

    /**
     * 客户端发布权限：学生和老师完全一致，都可以发布无学分活动，不再需要发布资格审批。
     *
     * <p>运营人员（STAFF）是管理端专属账号，只能从管理端发布学分活动；
     * 管理员负责审核，也不在客户端发布。
     */
    private void checkClientPublishPermission(UserInfo userInfo) {
        boolean allowed = UserRoleEnum.STUDENT.getCode().equals(userInfo.getUserRole())
                || UserRoleEnum.TEACHER.getCode().equals(userInfo.getUserRole());
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅学生和老师可以在客户端发布活动");
        }
    }

    /**
     * 客户端（学生/老师）只能发布运动健身、兴趣团建等无学分、无志愿时长的活动。
     * 学分与志愿时长具有公信力，必须由管理端账号发布。
     */
    private void validateNonCreditActivity(CreateActivityRequest request) {
        validateClientPublishType(request.getActivityType());
        validateClientHasNoAwardFields(
                request.getActivityCredit(), request.getCreditType(),
                request.getVolunteerHours(), request.getCreditTiers());
    }

    private void validateNonCreditActivity(UpdateActivityRequest request) {
        validateClientPublishType(request.getActivityType());
        validateClientHasNoAwardFields(
                request.getActivityCredit(), request.getCreditType(),
                request.getVolunteerHours(), request.getCreditTiers());
    }

    private void validateClientPublishType(Integer activityType) {
        if (ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学分活动需由管理端账号发布并提交审核");
        }
        if (ActivityTypeEnum.VOLUNTEER.getCode().equals(activityType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "志愿时长活动需由管理端账号发布并提交审核");
        }
        if (!ActivityTypeEnum.SPORTS.getCode().equals(activityType)
                && !ActivityTypeEnum.INTEREST.getCode().equals(activityType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "客户端仅支持发布运动健身或兴趣团建活动");
        }
    }

    private void validateClientHasNoAwardFields(BigDecimal activityCredit, Integer creditType,
                                                BigDecimal volunteerHours, List<CreditTierRequest> creditTiers) {
        if (activityCredit != null && activityCredit.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "客户端发布的活动不能设置学分");
        }
        if (creditType != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "客户端发布的活动不能设置学分类型");
        }
        if (volunteerHours != null && volunteerHours.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "客户端发布的活动不能设置志愿时长");
        }
        if (creditTiers != null && !creditTiers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "客户端发布的活动不能配置学分档位");
        }
    }

    /**
     * 校验活动管理权限。
     *
     * <p>老师与学生权限一致：除管理员外，任何人都只能管理自己发布的活动。
     */
    private void checkActivityManagePermission(ActivityMain activityMain, UserInfo operator) {
        if (activityMain.getPublisherUserId().equals(operator.getStudentNo())) {
            return;
        }
        if (!UserRoleEnum.ADMIN.getCode().equals(operator.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户没有活动管理权限");
        }
    }

    private void validateActivityTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动结束时间必须晚于开始时间");
        }
    }

    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return ".png";
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension) ? extension : ".png";
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
}
