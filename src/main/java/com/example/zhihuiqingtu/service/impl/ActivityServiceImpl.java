package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.config.UploadProperties;
import com.example.zhihuiqingtu.dto.activity.ActivityQueryRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivitySignInRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.dto.activity.CreateActivityRequest;
import com.example.zhihuiqingtu.dto.activity.UpdateActivityRequest;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.ActivityEnroll;
import com.example.zhihuiqingtu.entity.ActivityTag;
import com.example.zhihuiqingtu.entity.ActivityTagRel;
import com.example.zhihuiqingtu.entity.CreditRecord;
import com.example.zhihuiqingtu.entity.RecommendLog;
import com.example.zhihuiqingtu.entity.TeamMain;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.enums.ActivityStatusEnum;
import com.example.zhihuiqingtu.enums.ActivityTypeEnum;
import com.example.zhihuiqingtu.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.repository.RecommendLogRepository;
import com.example.zhihuiqingtu.repository.TeamMainRepository;
import com.example.zhihuiqingtu.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.service.ActivityService;
import com.example.zhihuiqingtu.vo.activity.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
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

/**
 * 活动服务实现。
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final ActivityMainRepository activityMainRepository;
    private final ActivityEnrollRepository activityEnrollRepository;
    private final ActivityTagRepository activityTagRepository;
    private final ActivityTagRelRepository activityTagRelRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final RecommendLogRepository recommendLogRepository;
    private final TeamMainRepository teamMainRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserInfoRepository userInfoRepository;
    private final UploadProperties uploadProperties;

    @Override
    public ActivityCoverUploadResponse uploadActivityCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(4109, "请先选择活动封面图片");
        }
        if (file.getSize() > uploadProperties.getMaxCoverSize().toBytes()) {
            throw new BusinessException(4110, "活动封面不能超过5MB");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(4111, "仅支持上传图片格式的活动封面");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        Path coverDir = Paths.get(uploadProperties.getActivityCoverDir()).toAbsolutePath().normalize();
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = coverDir.resolve(fileName).normalize();

        try {
            Files.createDirectories(coverDir);
            if (!target.startsWith(coverDir)) {
                throw new BusinessException(4112, "封面保存路径非法");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(4113, "活动封面上传失败，请稍后重试");
        }

        return ActivityCoverUploadResponse.builder()
                .coverUrl("/uploads/activity-covers/" + fileName)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse createActivity(CreateActivityRequest request) {
        UserInfo publisher = requireUser(request.getPublisherUserId());
        checkPublishPermission(publisher);
        validateActivityTime(request.getStartTime(), request.getEndTime());
        validateTags(request.getTagIds());
        validateActivityAttributes(
                request.getActivityType(),
                request.getActivityCredit(),
                request.getCreditType(),
                request.getVolunteerHours()
        );

        ActivityMain activityMain = ActivityMain.builder()
                .publisherUserId(request.getPublisherUserId())
                .activityTitle(request.getActivityTitle())
                .activityDesc(request.getActivityDesc())
                .activityType(request.getActivityType())
                .activityCredit(normalizeActivityCredit(request.getActivityType(), request.getActivityCredit()))
                .creditType(normalizeCreditType(request.getActivityType(), request.getCreditType()))
                .volunteerHours(normalizeVolunteerHours(request.getActivityType(), request.getVolunteerHours()))
                .coverUrl(normalizeCoverUrl(request.getCoverUrl()))
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .activityLocation(request.getActivityLocation())
                .maxParticipant(request.getMaxParticipant())
                .currentParticipant(0)
                .activityStatus(ActivityStatusEnum.PUBLISHED.getCode())
                .signInEnabled(0)
                .build();
        activityMainRepository.insert(activityMain);
        syncActivityTags(activityMain.getActivityId(), request.getTagIds());
        return getActivityDetail(activityMain.getActivityId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse updateActivity(Long activityId, UpdateActivityRequest request) {
        ActivityMain activityMain = requireActivity(activityId);
        UserInfo operator = requireUser(request.getOperatorUserId());
        checkActivityManagePermission(activityMain, operator);
        validateActivityTime(request.getStartTime(), request.getEndTime());
        validateTags(request.getTagIds());
        validateActivityAttributes(
                request.getActivityType(),
                request.getActivityCredit(),
                request.getCreditType(),
                request.getVolunteerHours()
        );

        if (request.getMaxParticipant() < activityMain.getCurrentParticipant()) {
            throw new BusinessException(4101, "最大参与人数不能小于当前已报名人数");
        }

        activityMain.setActivityTitle(request.getActivityTitle());
        activityMain.setActivityDesc(request.getActivityDesc());
        activityMain.setActivityType(request.getActivityType());
        activityMain.setActivityCredit(normalizeActivityCredit(request.getActivityType(), request.getActivityCredit()));
        activityMain.setCreditType(normalizeCreditType(request.getActivityType(), request.getCreditType()));
        activityMain.setVolunteerHours(normalizeVolunteerHours(request.getActivityType(), request.getVolunteerHours()));
        activityMain.setCoverUrl(normalizeCoverUrl(request.getCoverUrl()));
        activityMain.setStartTime(request.getStartTime());
        activityMain.setEndTime(request.getEndTime());
        activityMain.setActivityLocation(request.getActivityLocation());
        activityMain.setMaxParticipant(request.getMaxParticipant());
        activityMainRepository.updateById(activityMain);
        syncActivityTags(activityId, request.getTagIds());
        return getActivityDetail(activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse changeActivityStatus(Long activityId, ChangeActivityStatusRequest request) {
        ActivityMain activityMain = requireActivity(activityId);
        UserInfo operator = requireUser(request.getOperatorUserId());
        checkActivityManagePermission(activityMain, operator);

        if (!ActivityStatusEnum.isPersistentStatus(request.getActivityStatus())) {
            throw new BusinessException(4104, "活动状态仅支持切换为已发布或已取消");
        }
        activityMain.setActivityStatus(request.getActivityStatus());
        activityMainRepository.updateById(activityMain);
        return getActivityDetail(activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse updateSignInSwitch(Long activityId, ChangeActivitySignInRequest request) {
        ActivityMain activityMain = requireActivity(activityId);
        UserInfo operator = requireUser(request.getOperatorUserId());
        checkActivityManagePermission(activityMain, operator);

        if (!Integer.valueOf(0).equals(request.getSignInEnabled()) && !Integer.valueOf(1).equals(request.getSignInEnabled())) {
            throw new BusinessException(4105, "签到开关仅支持0或1");
        }
        if (Integer.valueOf(1).equals(request.getSignInEnabled())
                && ActivityStatusEnum.FINISHED.getCode().equals(resolveDisplayStatus(activityMain))) {
            throw new BusinessException(4106, "已结束活动不能再开启签到");
        }
        if (ActivityStatusEnum.CANCELED.getCode().equals(activityMain.getActivityStatus())
                && Integer.valueOf(1).equals(request.getSignInEnabled())) {
            throw new BusinessException(4107, "已取消活动不能开启签到");
        }

        activityMain.setSignInEnabled(request.getSignInEnabled());
        activityMainRepository.updateById(activityMain);
        return getActivityDetail(activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long activityId, String operatorUserId) {
        ActivityMain activityMain = requireActivity(activityId);
        UserInfo operator = requireUser(operatorUserId);
        checkActivityManagePermission(activityMain, operator);

        activityTagRelRepository.delete(new LambdaQueryWrapper<ActivityTagRel>()
                .eq(ActivityTagRel::getActivityId, activityId));
        activityEnrollRepository.delete(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getActivityId, activityId));
        creditRecordRepository.delete(new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getActivityId, activityId));
        recommendLogRepository.delete(new LambdaQueryWrapper<RecommendLog>()
                .eq(RecommendLog::getActivityId, activityId));

        List<TeamMain> teams = teamMainRepository.selectList(new LambdaQueryWrapper<TeamMain>()
                .eq(TeamMain::getActivityId, activityId));
        List<Long> teamIds = teams.stream().map(TeamMain::getTeamId).toList();
        if (!teamIds.isEmpty()) {
            teamMemberRepository.delete(new LambdaQueryWrapper<com.example.zhihuiqingtu.entity.TeamMember>()
                    .in(com.example.zhihuiqingtu.entity.TeamMember::getTeamId, teamIds));
            teamMainRepository.delete(new LambdaQueryWrapper<TeamMain>()
                    .in(TeamMain::getTeamId, teamIds));
        }
        activityMainRepository.deleteById(activityId);
    }

    @Override
    public PageResponse<ActivityResponse> queryActivities(ActivityQueryRequest request) {
        LambdaQueryWrapper<ActivityMain> queryWrapper = new LambdaQueryWrapper<ActivityMain>()
                .like(StringUtils.hasText(request.getKeyword()), ActivityMain::getActivityTitle, request.getKeyword())
                .eq(request.getActivityType() != null, ActivityMain::getActivityType, request.getActivityType())
                .eq(StringUtils.hasText(request.getPublisherUserId()), ActivityMain::getPublisherUserId, request.getPublisherUserId())
                .orderByDesc(ActivityMain::getCreateTime);

        if (request.getActivityStatus() != null) {
            if (ActivityStatusEnum.FINISHED.getCode().equals(request.getActivityStatus())) {
                queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode())
                        .lt(ActivityMain::getEndTime, LocalDateTime.now());
            } else if (ActivityStatusEnum.PUBLISHED.getCode().equals(request.getActivityStatus())) {
                queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.PUBLISHED.getCode());
            } else if (ActivityStatusEnum.CANCELED.getCode().equals(request.getActivityStatus())) {
                queryWrapper.eq(ActivityMain::getActivityStatus, ActivityStatusEnum.CANCELED.getCode());
            }
        }

        Page<ActivityMain> page = activityMainRepository.selectPage(
                new Page<>(request.getPageNo() + 1L, request.getPageSize()),
                queryWrapper
        );
        Page<ActivityResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toActivityResponse).toList());
        return PageResponse.from(responsePage);
    }

    @Override
    public ActivityResponse getActivityDetail(Long activityId) {
        return toActivityResponse(requireActivity(activityId));
    }

    @Override
    public List<ActivityTagVO> listActivityTags() {
        return activityTagRepository.selectList(new LambdaQueryWrapper<ActivityTag>()
                        .orderByAsc(ActivityTag::getTagId))
                .stream()
                .map(tag -> ActivityTagVO.builder()
                        .tagId(tag.getTagId())
                        .tagName(tag.getTagName())
                        .build())
                .toList();
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
            throw new BusinessException(4102, "存在无效的活动标签，请先调用 /api/activities/tags 查看可用标签ID");
        }
    }

    private void validateActivityAttributes(Integer activityType, BigDecimal activityCredit, Integer creditType, BigDecimal volunteerHours) {
        if (ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            if (activityCredit == null) {
                throw new BusinessException(4114, "第二课堂学分活动必须设置学分");
            }
            if (creditType == null) {
                throw new BusinessException(4115, "第二课堂学分活动必须选择学分类型");
            }
            if (!isValidCreditType(creditType)) {
                throw new BusinessException(4116, "学分类型不合法");
            }
            if (volunteerHours != null && volunteerHours.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(4117, "第二课堂学分活动不能设置志愿服务时长");
            }
            return;
        }

        if (ActivityTypeEnum.VOLUNTEER.getCode().equals(activityType)) {
            if (volunteerHours == null) {
                throw new BusinessException(4118, "志愿服务活动必须设置志愿服务时长");
            }
            if (activityCredit != null && activityCredit.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(4119, "志愿服务活动不允许设置学分");
            }
            if (creditType != null) {
                throw new BusinessException(4120, "志愿服务活动不允许设置学分类型");
            }
            return;
        }

        if (activityCredit != null && activityCredit.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(4121, "非第二课堂学分活动不允许设置学分");
        }
        if (creditType != null) {
            throw new BusinessException(4122, "非第二课堂学分活动不允许设置学分类型");
        }
        if (volunteerHours != null && volunteerHours.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(4123, "仅志愿服务活动允许设置志愿服务时长");
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

    private BigDecimal normalizeActivityCredit(Integer activityType, BigDecimal activityCredit) {
        if (ActivityTypeEnum.SECOND_CLASS.getCode().equals(activityType)) {
            return activityCredit;
        }
        return BigDecimal.ZERO;
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

    private String normalizeCoverUrl(String coverUrl) {
        return StringUtils.hasText(coverUrl) ? coverUrl.trim() : null;
    }

    private ActivityResponse toActivityResponse(ActivityMain activityMain) {
        List<ActivityTagRel> relations = activityTagRelRepository.selectList(
                new LambdaQueryWrapper<ActivityTagRel>().eq(ActivityTagRel::getActivityId, activityMain.getActivityId())
        );
        List<Long> tagIds = relations.stream().map(ActivityTagRel::getTagId).toList();
        Map<Long, ActivityTag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : activityTagRepository.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(ActivityTag::getTagId, Function.identity()));

        List<ActivityTagVO> tags = relations.stream()
                .map(relation -> tagMap.get(relation.getTagId()))
                .filter(tag -> tag != null)
                .map(tag -> ActivityTagVO.builder()
                        .tagId(tag.getTagId())
                        .tagName(tag.getTagName())
                        .build())
                .toList();

        return ActivityResponse.builder()
                .activityId(activityMain.getActivityId())
                .publisherUserId(activityMain.getPublisherUserId())
                .activityTitle(activityMain.getActivityTitle())
                .activityDesc(activityMain.getActivityDesc())
                .activityType(activityMain.getActivityType())
                .activityCredit(activityMain.getActivityCredit())
                .creditType(activityMain.getCreditType())
                .volunteerHours(activityMain.getVolunteerHours())
                .coverUrl(activityMain.getCoverUrl())
                .startTime(activityMain.getStartTime())
                .endTime(activityMain.getEndTime())
                .activityLocation(activityMain.getActivityLocation())
                .maxParticipant(activityMain.getMaxParticipant())
                .currentParticipant(activityMain.getCurrentParticipant())
                .activityStatus(activityMain.getActivityStatus())
                .displayStatus(resolveDisplayStatus(activityMain))
                .signInEnabled(activityMain.getSignInEnabled())
                .tags(tags)
                .build();
    }

    private void checkPublishPermission(UserInfo userInfo) {
        boolean allowed = UserRoleEnum.ADMIN.getCode().equals(userInfo.getUserRole())
                || UserRoleEnum.TEACHER.getCode().equals(userInfo.getUserRole())
                || Integer.valueOf(1).equals(userInfo.getPublisherStatus());
        if (!allowed) {
            throw new BusinessException(4103, "当前用户没有活动发布权限");
        }
    }

    private void checkActivityManagePermission(ActivityMain activityMain, UserInfo operator) {
        if (activityMain.getPublisherUserId().equals(operator.getStudentNo())) {
            return;
        }
        if (!UserRoleEnum.ADMIN.getCode().equals(operator.getUserRole())
                && !UserRoleEnum.TEACHER.getCode().equals(operator.getUserRole())) {
            throw new BusinessException(4108, "当前用户没有活动管理权限");
        }
    }

    private void validateActivityTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(4124, "活动结束时间必须晚于开始时间");
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
}
