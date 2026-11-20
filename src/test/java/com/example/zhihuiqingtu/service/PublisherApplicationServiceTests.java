package com.example.zhihuiqingtu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.publisher.dto.ApplyPublisherRequest;
import com.example.zhihuiqingtu.publisher.entity.PublisherPermissionRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordTypeEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.publisher.service.impl.PublisherApplicationServiceImpl;
import com.example.zhihuiqingtu.publisher.vo.PublisherApplicationStatusResponse;
import com.example.zhihuiqingtu.publisher.vo.PublisherPermissionRecordVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublisherApplicationServiceTests {

    private static final String USER_ID = "S0001";

    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final PublisherPermissionRecordRepository recordRepository =
            mock(PublisherPermissionRecordRepository.class);
    private final PublisherApplicationServiceImpl service = new PublisherApplicationServiceImpl(
            currentUser, userInfoRepository, recordRepository);

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PublisherPermissionRecord.class);
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @Test
    void studentCanApplyWhenPermissionDisabled() {
        stubUser(UserRoleEnum.STUDENT.getCode(), PublisherStatusEnum.DISABLED.getCode());
        when(recordRepository.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(recordRepository.insert(any(PublisherPermissionRecord.class))).thenAnswer(invocation -> {
            PublisherPermissionRecord record = invocation.getArgument(0);
            record.setRecordId(10L);
            return 1;
        });

        PublisherPermissionRecordVO response = service.apply(applyRequest("  举办社团活动  "));

        assertEquals(10L, response.getRecordId());
        assertEquals(PublisherRecordTypeEnum.APPLY.getCode(), response.getRecordType());
        assertEquals(PublisherRecordStatusEnum.PENDING.getCode(), response.getRecordStatus());
        assertEquals("举办社团活动", response.getApplyReason());
    }

    @Test
    void cannotApplyWhenAlreadyEnabled() {
        stubUser(UserRoleEnum.STUDENT.getCode(), PublisherStatusEnum.ENABLED.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.apply(applyRequest("申请理由")));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(recordRepository, never()).insert(any(PublisherPermissionRecord.class));
    }

    @Test
    void cannotApplyWhenPendingApplicationExists() {
        stubUser(UserRoleEnum.STUDENT.getCode(), PublisherStatusEnum.DISABLED.getCode());
        when(recordRepository.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.apply(applyRequest("申请理由")));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(recordRepository, never()).insert(any(PublisherPermissionRecord.class));
    }

    @Test
    void privilegedRoleDoesNotNeedApplication() {
        stubUser(UserRoleEnum.ADMIN.getCode(), PublisherStatusEnum.DISABLED.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.apply(applyRequest("申请理由")));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    @Test
    void concurrentDuplicateSubmissionReturnsConflict() {
        stubUser(UserRoleEnum.STUDENT.getCode(), PublisherStatusEnum.DISABLED.getCode());
        when(recordRepository.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(recordRepository.insert(any(PublisherPermissionRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_publisher_pending"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.apply(applyRequest("申请理由")));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    @Test
    void myStatusAllowsApplyWhenNeverApplied() {
        stubUser(UserRoleEnum.STUDENT.getCode(), PublisherStatusEnum.DISABLED.getCode());
        when(recordRepository.selectOne(any(Wrapper.class))).thenReturn(null);

        PublisherApplicationStatusResponse response = service.getMyStatus();

        assertEquals(PublisherStatusEnum.DISABLED.getCode(), response.getPublisherStatus());
        assertTrue(response.getCanApply());
        assertFalse(response.getPending());
        assertEquals(null, response.getLatestApplication());
    }

    @Test
    void pendingApplicationDisablesFurtherApply() {
        stubUser(UserRoleEnum.STUDENT.getCode(), PublisherStatusEnum.DISABLED.getCode());
        when(recordRepository.selectOne(any(Wrapper.class))).thenReturn(PublisherPermissionRecord.builder()
                .recordId(1L)
                .studentNo(USER_ID)
                .recordType(PublisherRecordTypeEnum.APPLY.getCode())
                .recordStatus(PublisherRecordStatusEnum.PENDING.getCode())
                .applyReason("申请理由")
                .build());

        PublisherApplicationStatusResponse response = service.getMyStatus();

        assertTrue(response.getPending());
        assertFalse(response.getCanApply());
    }

    private void stubUser(int role, int publisherStatus) {
        when(currentUser.requireUserId()).thenReturn(USER_ID);
        when(userInfoRepository.selectById(USER_ID)).thenReturn(UserInfo.builder()
                .studentNo(USER_ID)
                .userName("测试学生")
                .userRole(role)
                .publisherStatus(publisherStatus)
                .build());
    }

    private ApplyPublisherRequest applyRequest(String reason) {
        ApplyPublisherRequest request = new ApplyPublisherRequest();
        request.setApplyReason(reason);
        return request;
    }
}
