package com.example.zhihuiqingtu.admin;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.admin.support.AdminGuard;

class AdminGuardTests {

    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final AdminGuard adminGuard = new AdminGuard(currentUser, userInfoRepository);

    @Test
    void adminCanPass() {
        stubUser(UserRoleEnum.ADMIN.getCode());

        assertEquals("A0001", adminGuard.requireAdmin());
    }

    @Test
    void studentIsForbidden() {
        stubUser(UserRoleEnum.STUDENT.getCode());

        BusinessException exception = assertThrows(BusinessException.class, adminGuard::requireAdmin);

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void deletedOrMissingUserIsForbidden() {
        when(currentUser.requireUserId()).thenReturn("A0001");
        when(userInfoRepository.selectById("A0001")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, adminGuard::requireAdmin);

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    private void stubUser(int roleCode) {
        when(currentUser.requireUserId()).thenReturn("A0001");
        when(userInfoRepository.selectById("A0001")).thenReturn(UserInfo.builder()
                .studentNo("A0001")
                .userRole(roleCode)
                .build());
    }
}
