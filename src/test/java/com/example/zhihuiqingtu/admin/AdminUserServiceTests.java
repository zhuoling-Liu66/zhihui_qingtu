package com.example.zhihuiqingtu.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.publisher.entity.PublisherPermissionRecord;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.AccountStatusEnum;
import com.example.zhihuiqingtu.publisher.enums.PublisherRecordTypeEnum;
import com.example.zhihuiqingtu.user.enums.PublisherStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.support.UserRevocationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdateAccountStatusRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdatePublisherStatusRequest;
import com.example.zhihuiqingtu.admin.user.dto.AdminUpdateRoleRequest;
import com.example.zhihuiqingtu.admin.user.service.AdminUserService;
import com.example.zhihuiqingtu.admin.user.vo.AdminUserVO;

class AdminUserServiceTests {

    private static final String OPERATOR_ID = "A0001";
    private static final String TARGET_ID = "S0001";

    private final AdminGuard adminGuard = mock(AdminGuard.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final PublisherPermissionRecordRepository publisherRecordRepository =
            mock(PublisherPermissionRecordRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserRevocationService userRevocationService = mock(UserRevocationService.class);
    private final AdminUserService adminUserService = new AdminUserService(
            adminGuard, userInfoRepository, publisherRecordRepository, passwordEncoder, userRevocationService);

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯 Mockito 单测没有 Spring 容器，Lambda 条件构造器需要手动初始化实体元数据
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
        TableInfoHelper.initTableInfo(assistant, PublisherPermissionRecord.class);
    }

    @Test
    void adminCanPromoteStudentToTeacher() {
        stubOperator();
        stubTarget(UserRoleEnum.STUDENT.getCode());
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AdminUserVO response = adminUserService.updateRole(TARGET_ID, roleRequest(UserRoleEnum.TEACHER.getCode()));

        assertEquals(UserRoleEnum.TEACHER.getCode(), response.getUserRole());
        verify(userInfoRepository).update(isNull(), any(Wrapper.class));
    }

    @Test
    void cannotChangeOwnRole() {
        stubOperator();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateRole(OPERATOR_ID, roleRequest(UserRoleEnum.STUDENT.getCode())));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(userInfoRepository, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void invalidRoleIsRejected() {
        stubOperator();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateRole(TARGET_ID, roleRequest(9)));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    void cannotDemoteLastAdmin() {
        stubOperator();
        stubTarget(UserRoleEnum.ADMIN.getCode());
        when(userInfoRepository.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateRole(TARGET_ID, roleRequest(UserRoleEnum.STUDENT.getCode())));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(userInfoRepository, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void canDemoteAdminWhenAnotherAdminExists() {
        stubOperator();
        stubTarget(UserRoleEnum.ADMIN.getCode());
        when(userInfoRepository.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AdminUserVO response = adminUserService.updateRole(TARGET_ID, roleRequest(UserRoleEnum.STUDENT.getCode()));

        assertEquals(UserRoleEnum.STUDENT.getCode(), response.getUserRole());
    }

    @Test
    void missingTargetUserReturnsNotFound() {
        stubOperator();
        when(userInfoRepository.selectById(TARGET_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateRole(TARGET_ID, roleRequest(UserRoleEnum.TEACHER.getCode())));

        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
    }

    @Test
    void adminCanRevokePublisherStatus() {
        stubOperator();
        stubTarget(UserRoleEnum.STUDENT.getCode());
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(publisherRecordRepository.update(isNull(), any(Wrapper.class))).thenReturn(0);

        AdminUpdatePublisherStatusRequest request = new AdminUpdatePublisherStatusRequest();
        request.setPublisherStatus(PublisherStatusEnum.DISABLED.getCode());
        AdminUserVO response = adminUserService.updatePublisherStatus(TARGET_ID, request);

        assertEquals(PublisherStatusEnum.DISABLED.getCode(), response.getPublisherStatus());

        ArgumentCaptor<PublisherPermissionRecord> recordCaptor =
                ArgumentCaptor.forClass(PublisherPermissionRecord.class);
        verify(publisherRecordRepository).insert(recordCaptor.capture());
        assertEquals(PublisherRecordTypeEnum.REVOKE.getCode(),
                recordCaptor.getValue().getRecordType());
        assertEquals(OPERATOR_ID, recordCaptor.getValue().getOperatorUserId());
    }

    @Test
    void samePublisherStatusDoesNotCreateAuditRecord() {
        stubOperator();
        stubTarget(UserRoleEnum.STUDENT.getCode());

        AdminUpdatePublisherStatusRequest request = new AdminUpdatePublisherStatusRequest();
        request.setPublisherStatus(PublisherStatusEnum.ENABLED.getCode());
        AdminUserVO response = adminUserService.updatePublisherStatus(TARGET_ID, request);

        assertEquals(PublisherStatusEnum.ENABLED.getCode(), response.getPublisherStatus());
        verify(publisherRecordRepository, never()).insert(any(PublisherPermissionRecord.class));
        verify(userInfoRepository, never()).update(isNull(), any(Wrapper.class));
    }

    private void stubOperator() {
        when(adminGuard.requireAdmin()).thenReturn(OPERATOR_ID);
    }

    private void stubTarget(int roleCode) {
        stubTarget(roleCode, AccountStatusEnum.NORMAL.getCode());
    }

    private void stubTarget(int roleCode, int accountStatus) {
        when(userInfoRepository.selectById(TARGET_ID)).thenReturn(UserInfo.builder()
                .studentNo(TARGET_ID)
                .userRole(roleCode)
                .publisherStatus(PublisherStatusEnum.ENABLED.getCode())
                .accountStatus(accountStatus)
                .build());
    }

    private AdminUpdateRoleRequest roleRequest(int roleCode) {
        AdminUpdateRoleRequest request = new AdminUpdateRoleRequest();
        request.setUserRole(roleCode);
        return request;
    }

    private AdminUpdateAccountStatusRequest statusRequest(int accountStatus) {
        AdminUpdateAccountStatusRequest request = new AdminUpdateAccountStatusRequest();
        request.setAccountStatus(accountStatus);
        return request;
    }

    // ─────────────────── 禁用 / 启用 / 注销运营账号 ───────────────────

    @Test
    void adminCanDisableStaffAndRevokeTokens() {
        stubOperator();
        stubTarget(UserRoleEnum.STAFF.getCode());
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AdminUserVO response = adminUserService.updateAccountStatus(
                TARGET_ID, statusRequest(AccountStatusEnum.DISABLED.getCode()));

        assertEquals(AccountStatusEnum.DISABLED.getCode(), response.getAccountStatus());
        // 禁用必须同时撤销已签发令牌，否则旧令牌在过期前依然可用
        verify(userRevocationService).revoke(TARGET_ID);
    }

    @Test
    void enablingStaffKeepsRevocationMarker() {
        stubOperator();
        stubTarget(UserRoleEnum.STAFF.getCode(), AccountStatusEnum.DISABLED.getCode());
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AdminUserVO response = adminUserService.updateAccountStatus(
                TARGET_ID, statusRequest(AccountStatusEnum.NORMAL.getCode()));

        assertEquals(AccountStatusEnum.NORMAL.getCode(), response.getAccountStatus());
        // 启用不能撤销/删除标记：禁用前的旧令牌必须保持失效
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void sameAccountStatusIsNoOp() {
        stubOperator();
        stubTarget(UserRoleEnum.STAFF.getCode(), AccountStatusEnum.DISABLED.getCode());

        AdminUserVO response = adminUserService.updateAccountStatus(
                TARGET_ID, statusRequest(AccountStatusEnum.DISABLED.getCode()));

        assertEquals(AccountStatusEnum.DISABLED.getCode(), response.getAccountStatus());
        verify(userInfoRepository, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void invalidAccountStatusIsRejected() {
        stubOperator();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateAccountStatus(TARGET_ID, statusRequest(9)));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void cannotDisableStudentAccount() {
        stubOperator();
        stubTarget(UserRoleEnum.STUDENT.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateAccountStatus(
                        TARGET_ID, statusRequest(AccountStatusEnum.DISABLED.getCode())));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        verify(userInfoRepository, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void cannotDisableAdminAccount() {
        stubOperator();
        stubTarget(UserRoleEnum.ADMIN.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateAccountStatus(
                        TARGET_ID, statusRequest(AccountStatusEnum.DISABLED.getCode())));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void nonAdminCannotChangeAccountStatus() {
        when(adminGuard.requireAdmin())
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可以执行该操作"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.updateAccountStatus(
                        TARGET_ID, statusRequest(AccountStatusEnum.DISABLED.getCode())));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void adminCanDeleteStaffAndRevokeTokens() {
        stubOperator();
        stubTarget(UserRoleEnum.STAFF.getCode());

        adminUserService.deleteStaff(TARGET_ID);

        // deleteById 经 @TableLogic 翻译为软删除
        verify(userInfoRepository).deleteById(TARGET_ID);
        verify(userRevocationService).revoke(TARGET_ID);
    }

    @Test
    void cannotDeleteStudentAccount() {
        stubOperator();
        stubTarget(UserRoleEnum.STUDENT.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.deleteStaff(TARGET_ID));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        verify(userInfoRepository, never()).deleteById(TARGET_ID);
        verifyNoInteractions(userRevocationService);
    }

    @Test
    void cannotDeleteAdminAccount() {
        stubOperator();
        stubTarget(UserRoleEnum.ADMIN.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.deleteStaff(TARGET_ID));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
        verify(userInfoRepository, never()).deleteById(TARGET_ID);
    }

    @Test
    void deleteMissingStaffReturnsNotFound() {
        stubOperator();
        when(userInfoRepository.selectById(TARGET_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminUserService.deleteStaff(TARGET_ID));

        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
        verifyNoInteractions(userRevocationService);
    }
}
