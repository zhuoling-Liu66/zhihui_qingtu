package com.example.zhihuiqingtu.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.security.jwt.JwtProperties;
import com.example.zhihuiqingtu.security.jwt.JwtUtil;
import com.example.zhihuiqingtu.activity.controller.ActivityController;
import com.example.zhihuiqingtu.enrollment.controller.EnrollmentController;
import com.example.zhihuiqingtu.team.controller.TeamController;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.user.enums.AccountStatusEnum;
import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import com.example.zhihuiqingtu.exception.GlobalExceptionHandler;
import com.example.zhihuiqingtu.publisher.repository.PublisherPermissionRecordRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CompositeBearerTokenResolver;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.security.JwtBlacklistValidator;
import com.example.zhihuiqingtu.security.UserRevocationValidator;
import com.example.zhihuiqingtu.activity.service.ActivityService;
import com.example.zhihuiqingtu.enrollment.service.EnrollmentService;
import com.example.zhihuiqingtu.team.service.TeamService;
import com.example.zhihuiqingtu.security.support.TokenBlacklistService;
import com.example.zhihuiqingtu.security.support.UserRevocationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.zhihuiqingtu.admin.user.controller.AdminUserController;
import com.example.zhihuiqingtu.admin.user.service.AdminUserService;

/**
 * 安全过滤链的回归测试：匿名浏览放行 + 管理端账号治理鉴权 + 令牌撤销。
 *
 * <p>需求一：未登录也能浏览活动（列表/详情/标签/热门/名额）与队伍（列表/详情），
 * 但报名、发布、入队等写操作以及个人数据查询仍必须登录。
 * 这些规则声明在 {@link SecurityConfig} 的 URL 授权里，且<b>顺序敏感</b>
 * （放行必须写在角色规则之前），纯 Service 单测覆盖不到，故用 MockMvc 走真实过滤器链。
 *
 * <p>需求二：管理员可禁用/启用/注销运营账号。URL 级只拦到 ADMIN/STAFF，
 * "仅管理员、仅 STAFF 目标"由 AdminGuard + Service 校验，这里走真实 Bean 验证整条链；
 * 同时覆盖"按用户撤销标记使已签发令牌立即 401"的解码器接线。
 *
 * <p>只装配 Web 切片 + 安全配置，Service/Repository 全部 mock，不依赖 MySQL/Redis，
 * 与仓库既有单测"随处可跑"的约定一致。令牌用项目自己的 {@link JwtUtil} 签发，
 * 顺带覆盖"解码器 + 角色 claim 转换 + 黑名单 + 按用户撤销"整条认证链路。
 */
@WebMvcTest(controllers = {ActivityController.class, TeamController.class, EnrollmentController.class,
        AdminUserController.class})
// 主配置不做组件扫描（见 SliceApplication），控制器与安全配置需显式注册
@Import({SecurityConfig.class, ActivityController.class, TeamController.class, EnrollmentController.class,
        AdminUserController.class, AdminUserService.class, AdminGuard.class, CurrentUser.class,
        GlobalExceptionHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class})
class AnonymousBrowsingSecurityTests {

    /** HS512 要求密钥至少 64 字节，这里给足长度；仅测试用，与生产配置无关。 */
    private static final String TEST_SECRET =
            "zhihuiqingtu-security-test-secret-0123456789-0123456789-0123456789";

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 切片不带 MyBatis 自动配置，而 AdminUserService 真实执行时用到 Lambda 条件构造器，
        // 需要手动初始化实体元数据（与纯 Mockito 单测同一套路）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    // 用 @MockitoBean 而非 @Bean mock：每个用例后自动重置，
    // 避免 verify(revoke) 的调用计数跨用例累积
    @MockitoBean
    private UserRevocationService userRevocationService;

    @MockitoBean
    private ActivityService activityService;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private EnrollmentService enrollmentService;

    @MockitoBean
    private UserInfoRepository userInfoRepository;

    @MockitoBean
    private PublisherPermissionRecordRepository publisherRecordRepository;

    /**
     * 顶替启动类作为切片的主配置：启动类带 @MapperScan 与
     * @EnableConfigurationProperties(JwtProperties)，前者在没有 MyBatis 自动配置的
     * 切片里会造出缺 SqlSessionFactory 的 Mapper 工厂 bean，后者会因测试环境
     * 没有 jwt.secret 而绑定失败。嵌套 @SpringBootConfiguration 会被切片优先发现，
     * 从而绕开启动类的全量组件扫描。
     */
    @SpringBootConfiguration
    static class SliceApplication {
    }

    @TestConfiguration
    static class SecurityTestBeans {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(TEST_SECRET, "HS512", 3_600_000L);
        }

        @Bean
        JwtUtil jwtUtil(JwtProperties jwtProperties) {
            return new JwtUtil(jwtProperties);
        }

        @Bean
        TokenBlacklistService tokenBlacklistService() {
            // 默认 isBlacklisted=false（Mockito 默认返回值），黑名单场景在用例里单独 stub
            return mock(TokenBlacklistService.class);
        }

        @Bean
        JwtBlacklistValidator jwtBlacklistValidator(TokenBlacklistService tokenBlacklistService) {
            return new JwtBlacklistValidator(tokenBlacklistService);
        }

        @Bean
        UserRevocationValidator userRevocationValidator(UserRevocationService userRevocationService) {
            return new UserRevocationValidator(userRevocationService);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            // AdminUserService 依赖它；本切片不测密码逻辑，mock 即可
            return mock(PasswordEncoder.class);
        }

        @Bean
        CompositeBearerTokenResolver compositeBearerTokenResolver() {
            return new CompositeBearerTokenResolver("token");
        }

        @Bean
        JsonMapper jsonMapper() {
            // SecurityConfig 与消息转换器共用；切片不带 Jackson 自动配置时由此兜底
            return JsonMapper.builder().build();
        }
    }

    // ─────────────────────────── 匿名可浏览 ───────────────────────────

    @Test
    void anonymousCanBrowseActivityList() throws Exception {
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void anonymousCanBrowseActivityDetailStockTagsAndHot() throws Exception {
        mockMvc.perform(get("/api/activities/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities/1/stock")).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities/tags")).andExpect(status().isOk());
        mockMvc.perform(get("/api/activities/hot")).andExpect(status().isOk());
    }

    @Test
    void anonymousCanBrowseTeamListAndDetail() throws Exception {
        mockMvc.perform(get("/api/teams")).andExpect(status().isOk());
        mockMvc.perform(get("/api/teams/1")).andExpect(status().isOk());
    }

    // ─────────────────────────── 匿名不可写 ───────────────────────────

    @Test
    void anonymousCannotEnroll() throws Exception {
        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void anonymousCannotPublishActivity() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotJoinTeam() throws Exception {
        mockMvc.perform(post("/api/teams/1/join"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadPersonalData() throws Exception {
        mockMvc.perform(get("/api/enrollments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/2021001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/credits/statistics")).andExpect(status().isUnauthorized());
    }

    // ─────────────────────────── 登录态不受影响 ───────────────────────────

    @Test
    void loggedInStudentCanBrowseAndEnroll() throws Exception {
        String token = jwtUtil.generateToken("2021001", 1);
        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void staffTokenStillCannotPublishFromClient() throws Exception {
        String token = jwtUtil.generateToken("S0001", 4);
        mockMvc.perform(post("/api/activities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────── 放行不等于不验令牌 ───────────────────────────

    @Test
    void invalidTokenStillRejectedOnPublicBrowsePath() throws Exception {
        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blacklistedTokenStillRejectedOnPublicBrowsePath() throws Exception {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(true);
        String token = jwtUtil.generateToken("2021001", 1);
        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────── 按用户撤销：禁用/注销立即生效 ───────────────────

    @Test
    void tokenIssuedBeforeUserRevocationIsRejected() throws Exception {
        // 撤销时刻在未来：任何已签发令牌（iat ≤ 撤销时刻）都应被拒
        when(userRevocationService.revokedAtEpochSecond("2021001"))
                .thenReturn(Instant.now().getEpochSecond() + 60);
        String token = jwtUtil.generateToken("2021001", 1);
        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIssuedAfterUserRevocationStillPasses() throws Exception {
        // 启用后重新登录的新令牌（iat > 撤销时刻）不受旧标记影响
        when(userRevocationService.revokedAtEpochSecond("2021001"))
                .thenReturn(Instant.now().getEpochSecond() - 60);
        String token = jwtUtil.generateToken("2021001", 1);
        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ─────────────────── 管理端账号治理：仅管理员、仅 STAFF 目标 ───────────────────

    @Test
    void anonymousCannotDisableOrDeleteAccounts() throws Exception {
        mockMvc.perform(patch("/api/admin/users/S0001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/users/S0001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotManageAccounts() throws Exception {
        String token = jwtUtil.generateToken("2021001", 1);
        mockMvc.perform(patch("/api/admin/users/S0001/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotDisableOrDeleteAccounts() throws Exception {
        // URL 级放行 STAFF 进管理端，但 AdminGuard 回查数据库后拒绝非管理员
        when(userInfoRepository.selectById("S0001")).thenReturn(staffUser());
        String token = jwtUtil.generateToken("S0001", 4);
        mockMvc.perform(patch("/api/admin/users/S0001/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(delete("/api/admin/users/S0001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDisableStaffAndTokensAreRevoked() throws Exception {
        stubAdminAndStaffTarget();
        when(userInfoRepository.update(isNull(), any(Wrapper.class))).thenReturn(1);
        String token = jwtUtil.generateToken("A0001", 2);

        mockMvc.perform(patch("/api/admin/users/S0001/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value(1));

        verify(userRevocationService).revoke("S0001");
    }

    @Test
    void adminCanDeleteStaffAndTokensAreRevoked() throws Exception {
        stubAdminAndStaffTarget();
        String token = jwtUtil.generateToken("A0001", 2);

        mockMvc.perform(delete("/api/admin/users/S0001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userInfoRepository).deleteById("S0001");
        verify(userRevocationService).revoke("S0001");
    }

    @Test
    void adminCannotDisableStudentThroughAdminEndpoint() throws Exception {
        when(userInfoRepository.selectById("A0001")).thenReturn(UserInfo.builder()
                .studentNo("A0001").userRole(UserRoleEnum.ADMIN.getCode()).build());
        when(userInfoRepository.selectById("2021001")).thenReturn(UserInfo.builder()
                .studentNo("2021001").userRole(UserRoleEnum.STUDENT.getCode()).build());
        String token = jwtUtil.generateToken("A0001", 2);

        mockMvc.perform(patch("/api/admin/users/2021001/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountStatus\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private void stubAdminAndStaffTarget() {
        when(userInfoRepository.selectById("A0001")).thenReturn(UserInfo.builder()
                .studentNo("A0001").userRole(UserRoleEnum.ADMIN.getCode()).build());
        when(userInfoRepository.selectById("S0001")).thenReturn(staffUser());
    }

    private UserInfo staffUser() {
        return UserInfo.builder()
                .studentNo("S0001")
                .userRole(UserRoleEnum.STAFF.getCode())
                .accountStatus(AccountStatusEnum.NORMAL.getCode())
                .build();
    }
}
