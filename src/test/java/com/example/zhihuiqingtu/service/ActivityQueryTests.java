package com.example.zhihuiqingtu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.config.UploadProperties;
import com.example.zhihuiqingtu.activity.dto.ActivityQueryRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.activity.entity.ActivityTag;
import com.example.zhihuiqingtu.activity.entity.ActivityTagRel;
import com.example.zhihuiqingtu.activity.repository.ActivityCreditTierRepository;
import com.example.zhihuiqingtu.enrollment.repository.ActivityEnrollRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRelRepository;
import com.example.zhihuiqingtu.activity.repository.ActivityTagRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.team.repository.TeamMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.activity.support.ActivityCacheService;
import com.example.zhihuiqingtu.activity.service.impl.ActivityServiceImpl;
import com.example.zhihuiqingtu.activity.support.ActivityStockService;
import com.example.zhihuiqingtu.activity.support.HotActivityService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.activity.service.impl.ActivityQueryServiceImpl;
import com.example.zhihuiqingtu.activity.support.ActivityAssembler;

/**
 * 活动查询（关键词 / 专题 / 分页）的定向测试。
 * <p>
 * 只验证生成的 SQL 条件，不连数据库：{@code LambdaQueryWrapper} 在单元测试里需要
 * 实体元数据缓存，因此在 {@link #initTableInfo()} 里手工初始化。
 */
@SuppressWarnings("unchecked")
class ActivityQueryTests {

    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final ActivityCreditTierRepository activityCreditTierRepository = mock(ActivityCreditTierRepository.class);
    private final ActivityEnrollRepository activityEnrollRepository = mock(ActivityEnrollRepository.class);
    private final ActivityTagRepository activityTagRepository = mock(ActivityTagRepository.class);
    private final ActivityTagRelRepository activityTagRelRepository = mock(ActivityTagRelRepository.class);
    private final CreditRecordRepository creditRecordRepository = mock(CreditRecordRepository.class);
    private final TeamMainRepository teamMainRepository = mock(TeamMainRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final UploadProperties uploadProperties = mock(UploadProperties.class);
    private final ActivityCacheService activityCacheService = mock(ActivityCacheService.class);
    private final ActivityStockService activityStockService = mock(ActivityStockService.class);
    private final HotActivityService hotActivityService = mock(HotActivityService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final ActivityAssembler activityAssembler = new ActivityAssembler(
            activityTagRelRepository,
            activityTagRepository,
            activityCreditTierRepository
    );
    private final ActivityQueryServiceImpl service = new ActivityQueryServiceImpl(
            activityMainRepository,
            activityTagRelRepository,
            activityTagRepository,
            activityEnrollRepository,
            activityCacheService,
            hotActivityService,
            activityAssembler,
            currentUser
    );

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ActivityMain.class);
        TableInfoHelper.initTableInfo(assistant, ActivityTag.class);
        TableInfoHelper.initTableInfo(assistant, ActivityTagRel.class);
    }

    @Test
    void keywordMatchesTitleDescriptionLocationAndTags() {
        when(activityTagRepository.selectList(any())).thenReturn(List.of(tag(1L, "志愿服务")));
        when(activityTagRelRepository.selectList(any())).thenReturn(List.of(
                rel(11L, 1L),
                rel(12L, 1L)
        ));
        stubSelectPage();

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setKeyword("志愿");
        service.queryActivities(request);

        String sql = capturedSql();
        assertTrue(sql.contains("activity_title"), "应匹配标题: " + sql);
        assertTrue(sql.contains("activity_desc"), "应匹配描述: " + sql);
        assertTrue(sql.contains("activity_location"), "应匹配地点: " + sql);
        assertTrue(sql.contains("activity_id IN"), "应把标签命中的活动并入查询: " + sql);
        // 标签查询是分两步做的：先标签名，再关联表；不能用 join 造成重复行
        verify(activityTagRepository).selectList(any());
        verify(activityTagRelRepository).selectList(any());
    }

    @Test
    void secondClassSectionRequiresPositiveCredit() {
        stubSelectPage();

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setSection("second-class");
        service.queryActivities(request);

        String sql = capturedSql();
        assertTrue(sql.contains("activity_type"), "应限定第二课堂: " + sql);
        assertTrue(sql.contains("activity_credit >"), "应要求学分大于 0: " + sql);
    }

    @Test
    void physicalSectionCombinesCreditTypeAndSportsType() {
        stubSelectPage();

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setSection("physical");
        service.queryActivities(request);

        String sql = capturedSql();
        // (第二课堂 且 体育学分) OR 运动健身活动
        assertTrue(sql.contains("credit_type"), "应包含体育学分条件: " + sql);
        assertTrue(sql.contains("OR"), "应包含活动类型组合条件: " + sql);
    }

    @Test
    void unknownSectionAddsNoCondition() {
        stubSelectPage();

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setSection("not-a-real-section");
        service.queryActivities(request);

        String sql = capturedSql();
        // 未知专题不加任何条件，避免客户端传任意字符串影响查询。
        // 只剩下固定排序片段是正常的（排序不依赖专题）。
        assertFalse(sql.contains("activity_type"), "未知专题不应限定类型: " + sql);
        assertFalse(sql.contains("credit_type"), "未知专题不应限定学分类型: " + sql);
        assertFalse(sql.contains("activity_credit"), "未知专题不应限定学分: " + sql);
    }

    @Test
    void publicQueryOnlyReturnsApprovedActivities() {
        stubSelectPage();

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setKeyword("志愿");
        service.queryActivities(request);

        String sql = capturedSql();
        // 公开列表必须带审核通过条件，否则待审核活动会提前曝光
        assertTrue(sql.contains("review_status"), "公开查询应限定审核状态: " + sql);
    }

    @Test
    void ownerQueryCanFilterOwnReviewStatus() {
        stubSelectPage();
        when(currentUser.userId()).thenReturn(java.util.Optional.of("P0001"));

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setPublisherUserId("P0001");
        request.setReviewStatus(0);
        service.queryActivities(request);

        String sql = capturedSql();
        // 查自己的活动时不再强制只查已通过，而是使用传入的审核状态筛选
        assertTrue(sql.contains("review_status"), "应包含审核状态条件: " + sql);
    }

    @Test
    void pageNoIsConvertedToOneBased() {
        stubSelectPage();

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setPageNo(2);
        request.setPageSize(12);
        service.queryActivities(request);

        ArgumentCaptor<com.baomidou.mybatisplus.extension.plugins.pagination.Page<ActivityMain>> pageCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class);
        verify(activityMainRepository).selectPage(pageCaptor.capture(), any(Wrapper.class));
        // 前端 pageNo 从 0 开始，MyBatis-Plus 从 1 开始
        assertEquals(3L, pageCaptor.getValue().getCurrent());
        assertEquals(12L, pageCaptor.getValue().getSize());
    }

    private void stubSelectPage() {
        when(currentUser.userId()).thenReturn(java.util.Optional.empty());
        when(activityMainRepository.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String capturedSql() {
        ArgumentCaptor<Wrapper<ActivityMain>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(activityMainRepository).selectPage(any(), wrapperCaptor.capture());
        return wrapperCaptor.getValue().getSqlSegment();
    }

    private ActivityTag tag(Long tagId, String tagName) {
        return ActivityTag.builder().tagId(tagId).tagName(tagName).build();
    }

    private ActivityTagRel rel(Long activityId, Long tagId) {
        return ActivityTagRel.builder().activityId(activityId).tagId(tagId).build();
    }
}
