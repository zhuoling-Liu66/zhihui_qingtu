package com.example.zhihuiqingtu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.security.jwt.JwtUtil;
import com.example.zhihuiqingtu.auth.dto.LoginRequest;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.AccountStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.auth.service.impl.AuthServiceImpl;
import com.example.zhihuiqingtu.security.support.TokenBlacklistService;
import com.example.zhihuiqingtu.auth.vo.LoginResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.security.UserRevocationValidator;

/**
 * 登录入口的账号状态校验。
 *
 * <p>禁用账号即使密码正确也不能拿到令牌；注销（软删除）账号由
 * {@code @TableLogic} 在查询层过滤，表现为"用户不存在"。
 * 已签发令牌的即时失效由 UserRevocationValidator 负责，不在本类覆盖范围。
 */
class AuthLoginStatusTests {

    private static final String STAFF_ID = "S0001";
    private static final String PASSWORD = "123456";

    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(
            userInfoRepository, jwtUtil, passwordEncoder, stringRedisTemplate, tokenBlacklistService);

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @BeforeEach
    void stubRedisAndPasswordEncoder() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 未锁定
        when(valueOperations.get(anyString())).thenReturn(null);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void disabledStaffCannotLoginAdminPortal() {
        stubUser(UserRoleEnum.STAFF.getCode(), AccountStatusEnum.DISABLED.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.loginAdmin(loginRequest()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(jwtUtil, never()).generateToken(anyString(), any());
    }

    @Test
    void normalStaffCanLoginAdminPortal() {
        stubUser(UserRoleEnum.STAFF.getCode(), AccountStatusEnum.NORMAL.getCode());
        when(jwtUtil.generateToken(STAFF_ID, UserRoleEnum.STAFF.getCode())).thenReturn("token");

        LoginResponse response = authService.loginAdmin(loginRequest());

        assertEquals("token", response.getToken());
    }

    @Test
    void disabledStudentCannotLoginClientPortal() {
        stubUser(UserRoleEnum.STUDENT.getCode(), AccountStatusEnum.DISABLED.getCode());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(jwtUtil, never()).generateToken(anyString(), any());
    }

    @Test
    void softDeletedUserIsNotFoundOnLogin() {
        // @TableLogic 使 selectOne 查不到 delete_flag=1 的行，这里模拟其结果
        when(userInfoRepository.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.loginAdmin(loginRequest()));

        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
        verify(jwtUtil, never()).generateToken(anyString(), any());
    }

    @Test
    void disabledCheckDoesNotAccumulateLoginFailures() {
        stubUser(UserRoleEnum.STAFF.getCode(), AccountStatusEnum.DISABLED.getCode());

        assertThrows(BusinessException.class, () -> authService.loginAdmin(loginRequest()));

        // 密码正确但账号被禁：不应计入爆破失败次数（密码已验证通过）
        verify(valueOperations, never()).increment(anyString());
    }

    private void stubUser(int roleCode, int accountStatus) {
        when(userInfoRepository.selectOne(any(Wrapper.class))).thenReturn(UserInfo.builder()
                .studentNo(STAFF_ID)
                .userRole(roleCode)
                .accountStatus(accountStatus)
                .passwordHash("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu")
                .build());
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setStudentNo(STAFF_ID);
        request.setPassword(PASSWORD);
        return request;
    }
}
