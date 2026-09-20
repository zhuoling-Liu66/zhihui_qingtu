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
import com.example.zhihuiqingtu.repository.TeamMainRepository;
import com.example.zhihuiqingtu.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.service.ActivityService;
import com.example.zhihuiqingtu.vo.activity.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityStockResponse;
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
        // 初始化 Redis 库存（此时 currentParticipant=0，remaining 就是 maxParticipant）。
        // 不做这一步也没关系 —— tryDeduct 遇到 key 不存在会回源加载，只是第一次报名会多一次查询。
        activityStockService.reset(activityMain.getActivityId(), activityMain.getMaxParticipant(), 0);
        // 新建的活动不可能已经在缓存里，所以这里【不需要】evict，这是梳理写路径时容易多写的一步。
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
        // 最大人数可能被改小/改大，按数据库权威值重算 Redis 库存。
        // 注意上面 4101 那条校验（最大人数不能小于当前报名人数）必须先过，否则会算出负库存。
        activityStockService.reset(activityId, activityMain.getMaxParticipant(), activityMain.getCurrentParticipant());
        // ★ 必须在下面 getActivityDetail 之前删缓存。
        // 因为 getActivityDetail 现在走 Cache-Aside 读路径：不先删，它会把【旧的】缓存原样返回，
        // 于是"改了活动但接口返回的还是老数据"（而且数据库已经改了，只有返回值是旧的，非常难排查）。
        activityCacheService.evict(activityId);
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
        // 活动状态变了 → 缓存里的 displayStatus / activityStatus 已过期，必须删缓存
        activityCacheService.evict(activityId);
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
        // sign_in_enabled 是 ActivityResponse 的一个字段，缓存必须失效 ——
        // 否则"刚开启签到但学生点签到仍提示未开放"（EnrollmentServiceImpl 里签到读的是库，不是缓存，
        // 但前端拿到的详情是缓存，会显示错误的按钮状态）。
        activityCacheService.evict(activityId);
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
        // 级联删除结束后清掉两个 Redis key：
        //   - 详情缓存：不删的话，已删除的活动在 TTL 内还能被查到（前端会看到"删除没生效"）
        //   - 库存 key：不删的话会留下一个永远没人清理的无主 key
        activityCacheService.evict(activityId);
        activityStockService.evict(activityId);
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
        // Cache-Aside 读路径：
        //   1. 先查 Redis，命中直接返回（连 toActivityResponse 里那两条标签查询的 SQL 都省了）；
        //   2. 命中"活动不存在"的空值标记 → 直接抛 4100，不去查库（防缓存穿透）；
        //   3. 都未命中才查库，查完写回缓存。
        ActivityResponse cached = activityCacheService.get(activityId);
        if (cached != null) {
            // 缓存里存的是"事实"（activityStatus + endTime），但对象里那个 displayStatus 字段
            // 是写入时算好并一起被序列化进 JSON 的 —— 取回来时它已经被冻住了。
            // 所以命中缓存后必须按 endTime 重算一次，否则活动结束后页面还会显示"进行中"。
            // 这就是"缓存事实、不缓存结论"这条原则在代码上的落地方式。
            cached.setDisplayStatus(ActivityStatusEnum.resolveDisplayStatus(
                    cached.getActivityStatus(), cached.getEndTime()));
            return cached;
        }
        if (activityCacheService.isKnownMissing(activityId)) {
            throw new BusinessException(4100, "活动不存在");
        }

        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            // 缓存一个短 TTL 的空值标记，挡住"反复请求不存在 ID"这类穿透流量
            activityCacheService.putMissing(activityId);
            throw new BusinessException(4100, "活动不存在");
        }

        ActivityResponse response = toActivityResponse(activityMain);
        activityCacheService.put(response);
        return response;
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

    @Override
    public List<ActivityResponse> listHotActivities(int limit) {
        List<Long> activityIds = hotActivityService.listHotActivityIds(limit);
        if (activityIds.isEmpty()) {
            // 榜单还没数据是正常状态，返回空数组而不是报错
            return List.of();
        }

        // ★ 必须批量查，不能 for 循环里逐个 selectById —— 那是典型的 N+1 查询：
        //   榜单 10 条就是 10 次数据库往返，而 selectBatchIds 只发 1 条 IN (...) SQL。
        //
        // selectBatchIds 会自动带上逻辑删除条件（delete_flag = 0），
        // 所以已删除的活动不会被查出来，天然满足"回表后活动已被删除就跳过"的要求。
        List<ActivityMain> activities = activityMainRepository.selectBatchIds(activityIds);
        if (activities.isEmpty()) {
            return List.of();
        }

        // selectBatchIds 返回的顺序不保证与入参一致（取决于数据库），
        // 而榜单的顺序本身就是信息（热度排名），所以这里按榜单顺序重新排列。
        Map<Long, ActivityMain> byId = activities.stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, Function.identity()));
        List<ActivityResponse> result = new ArrayList<>();
        for (Long activityId : activityIds) {
            ActivityMain activityMain = byId.get(activityId);
            if (activityMain == null) {
                // 榜单里残留了已删除/不存在的活动：跳过它，不要让整个接口失败
                hotActivityService.remove(activityId);
                continue;
            }
            result.add(toActivityResponse(activityMain));
        }
        return result;
    }

    @Override
    public ActivityStockResponse getActivityStock(Long activityId) {
        ActivityMain activityMain = requireActivity(activityId);
        Integer maxParticipant = activityMain.getMaxParticipant();
        int current = activityMain.getCurrentParticipant() == null ? 0 : activityMain.getCurrentParticipant();

        boolean limited = maxParticipant != null && maxParticipant > 0;
        return ActivityStockResponse.builder()
                .activityId(activityId)
                .maxParticipant(maxParticipant)
                .currentParticipant(current)
                // 注意：这里从数据库现算，不从 Redis 读。Redis 里的值是"闸门"，
                // 可能因为回补失败与真实值有短暂偏差；展示给用户的必须是权威值。
                .remaining(limited ? Math.max(0, maxParticipant - current) : null)
                .limited(limited)
                .build();
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
                // ★ 这里用 resolveDisplayStatus(activityMain) 现算，而不是从缓存里读一个存好的值。
                //
                // displayStatus（3=已结束）依赖 LocalDateTime.now()，是"随时间自动变化"的派生值。
                // 数据库里只存 activity_status = 1/4，前端看到的 3 是每次查询现算出来的。
                // 如果把算好的值缓存 10 分钟，一个 10:00 结束的活动在 10:00~10:10 会一直显示"进行中" ——
                // 而且数据库是对的、代码是对的，只有缓存是错的，排查时极易怀疑人生。
                //
                // 原则：缓存"事实"（activityStatus + endTime），不缓存"结论"（displayStatus）。
                // 注意：走缓存路径时返回的对象里也带着（可能过期的）displayStatus 字段，
                // 所以 ActivityCacheService 的调用方拿到结果后也要调一次本方法重算，见 getActivityDetail。
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
