package com.example.zhihuiqingtu.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.publisher.entity.PublisherPermissionRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordTypeEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.admin.publisher.dto.AdminReviewPublisherApplicationRequest;
import com.example.zhihuiqingtu.admin.publisher.service.AdminPublisherApplicationService;
import com.example.zhihuiqingtu.admin.publisher.vo.AdminPublisherApplicationVO;
import com.example.zhihuiqingtu.admin.support.AdminGuard;

class AdminPublisherApplicationServiceTests {

    private static final Long RECORD_ID = 1L;
    private static final String STUDENT_ID = "S0001";
    private static final String OPERATOR_ID = "A0001";

    private final AdminGuard adminGuard = mock(AdminGuard.class);
    private final PublisherPermissionRecordRepository recordRepository =
            mock(PublisherPermissionRecordRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final AdminPublisherApplicationService service = new AdminPublisherApplicationService(
            adminGuard, recordRepository, userInfoRepository);

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PublisherPermissionRecord.class);
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @Test
    void approveApplicationGrantsPublisherPermission() {
        stubPendingApplication(PublisherRecordTypeEnum.APPLY.getCode());
        when(recordRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AdminPublisherApplicationVO response = service.review(RECORD_ID, reviewRequest(1, null));

        assertEquals(PublisherRecordStatusEnum.APPROVED.getCode(), response.getRecordStatus());
        assertEquals(OPERATOR_ID, response.getOperatorUserId());
        verify(userInfoRepository).update(isNull(), any(Wrapper.class));
    }

    @Test
    void rejectRequiresReason() {
        stubPendingApplication(PublisherRecordTypeEnum.APPLY.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.review(RECORD_ID, reviewRequest(2, "  ")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        verify(recordRepository, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void rejectApplicationDoesNotGrantPermission() {
        stubPendingApplication(PublisherRecordTypeEnum.APPLY.getCode());
        when(recordRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AdminPublisherApplicationVO response = service.review(RECORD_ID, reviewRequest(2, "材料不充分"));

        assertEquals(PublisherRecordStatusEnum.REJECTED.getCode(), response.getRecordStatus());
        assertEquals("材料不充分", response.getRejectReason());
        verify(userInfoRepository, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void cannotReviewAlreadyProcessedApplication() {
        stubPendingApplication(PublisherRecordTypeEnum.APPLY.getCode());
        when(recordRepository.selectById(RECORD_ID)).thenReturn(PublisherPermissionRecord.builder()
                .recordId(RECORD_ID)
                .studentNo(STUDENT_ID)
                .recordType(PublisherRecordTypeEnum.APPLY.getCode())
                .recordStatus(PublisherRecordStatusEnum.APPROVED.getCode())
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.review(RECORD_ID, reviewRequest(1, null)));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    @Test
    void onlyStudentApplicationCanBeReviewed() {
        stubPendingApplication(PublisherRecordTypeEnum.GRANT.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.review(RECORD_ID, reviewRequest(1, null)));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    private void stubPendingApplication(int recordType) {
        when(adminGuard.requireAdmin()).thenReturn(OPERATOR_ID);
        when(recordRepository.selectById(RECORD_ID)).thenReturn(PublisherPermissionRecord.builder()
                .recordId(RECORD_ID)
                .studentNo(STUDENT_ID)
                .recordType(recordType)
                .recordStatus(PublisherRecordStatusEnum.PENDING.getCode())
                .applyReason("申请理由")
                .build());
        when(userInfoRepository.selectById(STUDENT_ID)).thenReturn(UserInfo.builder()
                .studentNo(STUDENT_ID)
                .userName("测试学生")
                .userRole(UserRoleEnum.STUDENT.getCode())
                .publisherStatus(PublisherStatusEnum.DISABLED.getCode())
                .build());
        when(userInfoRepository.selectById(OPERATOR_ID)).thenReturn(UserInfo.builder()
                .studentNo(OPERATOR_ID)
                .userName("管理员")
                .userRole(UserRoleEnum.ADMIN.getCode())
                .build());
    }

    private AdminReviewPublisherApplicationRequest reviewRequest(int status, String rejectReason) {
        AdminReviewPublisherApplicationRequest request = new AdminReviewPublisherApplicationRequest();
        request.setRecordStatus(status);
        request.setRejectReason(rejectReason);
        return request;
    }
}
