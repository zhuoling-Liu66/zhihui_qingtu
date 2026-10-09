package com.example.zhihuiqingtu.config;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.JwtProperties;
import com.example.zhihuiqingtu.security.CompositeBearerTokenResolver;
import com.example.zhihuiqingtu.security.JwtBlacklistValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全配置：JWT 认证与授权。
 *
 * <p><b>认证</b>：凭证为请求头中的 JWT，由 {@code oauth2ResourceServer} 的过滤器链完成验签、
 * 有效期校验与登出黑名单校验；算法与密钥取自 {@link JwtProperties}，与签发端共用同一配置，
 * 避免两端不一致导致"自己签的令牌验不过"。
 *
 * <p><b>授权</b>：URL 级规则见下方 {@link #securityFilterChain}。
 * 方法级授权（{@code @PreAuthorize}）的开关已通过 {@link EnableMethodSecurity} 打开，
 * 但<b>当前尚未在业务代码中使用</b>：角色判断由 {@code security/CurrentUser}
 * 从 JWT 的 {@code role} claim 读取后在 Service 内显式校验。
 * 若改为使用 {@code @PreAuthorize}，需注意其抛出的 {@code AccessDeniedException}
 * 需由 {@code GlobalExceptionHandler} 单独处理，否则会被兜底分支转成 5000。
 *
 * <p><b>维护约束</b>
 * <ol>
 *   <li>授权规则<b>按声明顺序匹配、先匹配先生效</b>，{@code anyRequest()} 必须置于末尾；</li>
 *   <li>新增放行路径时，路径必须与 Controller 的真实映射一致（注意 {@code /api} 前缀）；</li>
 *   <li>若鉴权凭证改为由 Cookie 携带，<b>必须重新启用 CSRF 防护</b>；</li>
 *   <li>401 与 403 的错误体统一为 {@code ApiResponse} 结构，改动需同步前端解析逻辑。</li>
 * </ol>
 *
 * @see com.example.zhihuiqingtu.security.CompositeBearerTokenResolver
 * @see com.example.zhihuiqingtu.security.JwtBlacklistValidator
 */
@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** 业务错误码：与 {@code GlobalExceptionHandler} 的编号习惯保持一致。 */
    private static final int UNAUTHORIZED_CODE = 401;
    private static final int FORBIDDEN_CODE = 403;

    private final ObjectMapper objectMapper;

    /** 令牌解析器：兼容标准 Authorization 头与项目历史的自定义 token 头。 */
    private final CompositeBearerTokenResolver compositeBearerTokenResolver;

    public SecurityConfig(ObjectMapper objectMapper,
                          CompositeBearerTokenResolver compositeBearerTokenResolver) {
        this.objectMapper = objectMapper;
        this.compositeBearerTokenResolver = compositeBearerTokenResolver;
    }

    // ══════════════════════════════════════════════════════════════
    // 一、JWT 解码器
    // ══════════════════════════════════════════════════════════════

    /**
     * 构造 JWT 解码器（验签 + 校验有效期 + 校验登出黑名单）。
     *
     * <p><b>密钥与算法取自 {@link JwtProperties}</b>，与签发端 {@code JwtUtil} 共用同一配置对象。
     * 这是必须的：两端算法或密钥只要有一处不一致，就会出现"自己签的 token 自己验不过"，
     * 症状是所有携带合法令牌的请求返回 401，且与"请求里根本没带令牌"的表现完全相同，
     * 排查成本很高。
     *
     * <p><b>必须显式指定算法</b>：{@code NimbusJwtDecoder.withSecretKey(key)} 若不指定，
     * 默认按 HS256 校验，与 HS384/HS512 签发的令牌不匹配。
     *
     * <p>解码器只负责"验签 + 有效期"；登出黑名单通过 {@link JwtBlacklistValidator} 追加，
     * 见下方说明。
     *
     * @param jwtProperties        JWT 配置（密钥、算法）
     * @param jwtBlacklistValidator 登出黑名单校验器
     * @return 配置好的解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties,
                                JwtBlacklistValidator jwtBlacklistValidator) {
        MacAlgorithm macAlgorithm = switch (jwtProperties.resolvedAlgorithm()) {
            case HS256 -> MacAlgorithm.HS256;
            case HS384 -> MacAlgorithm.HS384;
            case HS512 -> MacAlgorithm.HS512;
        };

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtProperties.secretKey())
                .macAlgorithm(macAlgorithm)
                .build();

        /*
         * 追加黑名单校验。
         *
         * setJwtValidator 是【替换】语义而非追加：不显式带上 JwtValidators.createDefault()，
         * 框架默认的 exp/nbf 校验就会被顶掉，导致【已过期的令牌永久有效】——
         * 这是静默的安全漏洞，不会报错、不会失败，只有安全测试才能发现。
         */
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), jwtBlacklistValidator));

        log.info("JWT 解码器已就绪：算法={}，已启用默认校验(exp/nbf) + 黑名单校验",
                macAlgorithm.name());
        return decoder;
    }

    // ══════════════════════════════════════════════════════════════
    // 二、过滤器链：认证与授权的装配入口
    // ══════════════════════════════════════════════════════════════

    /**
     * 安全过滤器链：装配认证与授权规则。
     *
     * <p>方法内的配置块顺序即为生效顺序，且其中若干项互为前提
     * （例如必须关闭表单登录，未认证请求才会返回 401 而非重定向），
     * 调整顺序或删减配置块前请先确认其作用。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * 关闭 CSRF 防护。
                 * 前提：鉴权凭证在请求头中由前端显式携带，浏览器不会自动附加，因此不存在
                 * CSRF 攻击面。若凭证改为 Cookie 携带，该前提不再成立，必须重新启用。
                 */
                .csrf(CsrfConfigurer::disable)

                /*
                 * 无状态：JWT 自带身份信息，服务端不创建 HttpSession。
                 * 否则会退化为有状态会话，水平扩容时需额外处理会话共享。
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * 关闭表单登录与 HTTP Basic —— 二者均为 Spring Security 默认开启。
                 * 不关闭时：未认证请求会被 302 重定向到登录页，而非返回 401 JSON；
                 * 浏览器还可能弹出原生认证对话框。前后端分离场景必须关闭。
                 */
                .formLogin(FormLoginConfigurer::disable)
                .httpBasic(HttpBasicConfigurer::disable)

                /*
                 * 授权规则。顺序即优先级：先放行，末尾兜底。
                 * 路径必须与 Controller 的真实映射完全一致（注意 /api 前缀）。
                 */
                .authorizeHttpRequests(auth -> auth
                        // 登录/注册：用于换取令牌，本身不能要求令牌
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        // 静态封面图：由 <img src> 直接引用，无法携带请求头
                        .requestMatchers("/uploads/**").permitAll()
                        // 接口文档
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 健康检查：供运维探针访问
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 其余一律要求已认证
                        .anyRequest().authenticated())

                /*
                 * 开启 JWT 校验。认证成功后框架将 JwtAuthenticationToken 写入 SecurityContextHolder。
                 *
                 * bearerTokenResolver 指定令牌的读取位置：默认实现只认标准头
                 * `Authorization: Bearer <token>`，无法读取本项目历史使用的裸头，
                 * 会导致令牌始终为空、所有请求 401（详见 CompositeBearerTokenResolver）。
                 */
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(compositeBearerTokenResolver)
                        .jwt(Customizer.withDefaults())
                        // 401 响应体保持项目统一的 ApiResponse 结构
                        .authenticationEntryPoint(authenticationEntryPoint()))

                /*
                 * 403 处理：已认证但权限不足。
                 * 与 401 的区别：401 表示凭证缺失或无效（应引导登录），
                 * 403 表示身份有效但无权访问（重新登录无意义）。
                 */
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));

        return http.build();
    }

    // ══════════════════════════════════════════════════════════════
    // 三、401 / 403 响应：保持 ApiResponse 结构不变
    // ══════════════════════════════════════════════════════════════

    /**
     * 401 处理器：未登录或 token 无效。
     * <p>
     * 仍然返回 {@code {code:4010, message:"...", data:null}}，
     * 让前端不必因为换了鉴权实现而改解析逻辑。
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            String message = resolveUnauthorizedMessage(authException);
            log.debug("鉴权失败 {} {}: {}", request.getMethod(), request.getRequestURI(), message);
            writeJson(response, HttpStatus.UNAUTHORIZED, ApiResponse.failure(UNAUTHORIZED_CODE, message));
        };
    }

    /**
     * 403 处理器：已登录但无权访问。
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.debug("权限不足 {} {}: {}", request.getMethod(), request.getRequestURI(),
                    accessDeniedException.getMessage());
            writeJson(response, HttpStatus.FORBIDDEN,
                    ApiResponse.failure(FORBIDDEN_CODE, "没有权限执行该操作"));
        };
    }

    /**
     * 把 Spring Security 的异常翻译成对用户友好的提示。
     * <p>
     * 单纯回一句"未认证"对排查没帮助；区分"没带 token"和"token 无效/过期"
     * 能让前端知道该引导登录还是提示重新登录。
     */
    private String resolveUnauthorizedMessage(AuthenticationException exception) {
        if (exception instanceof InvalidBearerTokenException) {
            return "登录已过期或令牌无效，请重新登录";
        }
        if (exception instanceof AuthenticationServiceException) {
            return "认证服务异常，请稍后重试";
        }
        return "未登录，请先登录";
    }

    /** 统一写出 JSON 响应（状态码 + Content-Type + 响应体，三者缺一不可）。 */
    private void writeJson(HttpServletResponse response, HttpStatus status, ApiResponse<?> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
