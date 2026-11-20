package com.example.zhihuiqingtu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.award.dto.CreditQueryRequest;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.award.enums.CreditTypeEnum;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.award.repository.CreditRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.award.service.impl.CreditServiceImpl;
import com.example.zhihuiqingtu.award.vo.CreditRecordResponse;
import com.example.zhihuiqingtu.award.vo.CreditStatisticsResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditServiceImplTests {

    private static final Long RECORD_ID = 1L;
    private static final Long ACTIVITY_ID = 11L;
    private static final String USER_ID = "S0001";

    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final CreditRecordRepository creditRecordRepository = mock(CreditRecordRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CreditServiceImpl service = new CreditServiceImpl(
            activityMainRepository,
            creditRecordRepository,
            userInfoRepository,
            currentUser
    );

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CreditRecord.class);
        TableInfoHelper.initTableInfo(assistant, ActivityMain.class);
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @Test
    void revokedPublisherStillSeesOwnActivityCreditRecords() {
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn("P0001");
        when(activityMainRepository.selectList(any(Wrapper.class)))
                .thenReturn(List.of(ActivityMain.builder().activityId(ACTIVITY_ID).build()));

        Page<CreditRecord> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(CreditRecord.builder()
                .recordId(RECORD_ID)
                .activityId(ACTIVITY_ID)
                .userId("S0001")
                .verifyStatus(ReviewStatusEnum.PENDING.getCode())
                .build()));
        when(creditRecordRepository.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(page);

        PageResponse<CreditRecordResponse> response = service.queryCredits(new CreditQueryRequest());

        assertEquals(1, response.getRecords().size());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<CreditRecord>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(creditRecordRepository).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        // 自己获得的学分 + 自己发布活动产生的学分都在可见范围内。
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("activity_id"));
    }

    @Test
    void statisticsCountsCreditsAndVolunteerHoursSeparately() {
        when(currentUser.hasRole(UserRoleEnum.ADMIN.getCode())).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        when(userInfoRepository.selectById(USER_ID))
                .thenReturn(UserInfo.builder().studentNo(USER_ID).userName("学生").build());
        when(creditRecordRepository.selectList(any(Wrapper.class))).thenReturn(List.of(
                // 第二课堂发放：2 学分 · 智育
                CreditRecord.builder()
                        .recordId(1L)
                        .userId(USER_ID)
                        .activityId(10L)
                        .credit(new BigDecimal("2.00"))
                        .creditType(CreditTypeEnum.INTELLECTUAL_EDUCATION.getCode())
                        .verifyStatus(ReviewStatusEnum.APPROVED.getCode())
                        .build(),
                // 志愿活动发放：时长 3.5 小时，学分 0、类型为空
                CreditRecord.builder()
                        .recordId(2L)
                        .userId(USER_ID)
                        .activityId(11L)
                        .credit(BigDecimal.ZERO)
                        .creditType(null)
                        .volunteerHours(new BigDecimal("3.50"))
                        .verifyStatus(ReviewStatusEnum.APPROVED.getCode())
                        .build()
        ));

        CreditStatisticsResponse statistics = service.getStatistics(null);

        assertEquals(new BigDecimal("2.00"), statistics.getTotalCredit());
        assertEquals(new BigDecimal("3.50"), statistics.getTotalVolunteerHours());
        assertEquals(new BigDecimal("2.00"), statistics.getIntellectualEducationCredit());
        assertEquals(BigDecimal.ZERO, statistics.getMoralEducationCredit());
    }
}
