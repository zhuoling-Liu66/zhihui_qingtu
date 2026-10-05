package com.example.zhihuiqingtu.config;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.security.CompositeBearerTokenResolver;
import com.example.zhihuiqingtu.security.JwtBlacklistValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 安全配置：JWT 认证 + 声明式授权。
 *
 * <h2>这个类替换掉了什么</h2>
 * <table border="1">
 *   <tr><th>原来（手写）</th><th>现在（框架）</th></tr>
 *   <tr><td>{@code TokenInterceptor} 手工验签、手工写 401 响应</td>
 *       <td>{@code .oauth2ResourceServer(jwt())} + {@link JwtDecoder}</td></tr>
 *   <tr><td>{@code WebConfig#addInterceptors} 的放行名单（且路径写错过）</td>
 *       <td>{@code authorizeHttpRequests(...).requestMatchers(...).permitAll()}</td></tr>
 *   <tr><td>Service 里各处 {@code checkXxxPermission} 手写 if 判断</td>
 *       <td>{@code @PreAuthorize}（需配合 {@link EnableMethodSecurity}）</td></tr>
 *   <tr><td>自己解析 token 取身份</td>
 *       <td>框架写入 {@code SecurityContextHolder}，见 {@code CurrentUser}</td></tr>
 * </table>
 *
 * <h2>为什么"弃用 .and() 链式"是必须的</h2>
 * Spring Security 7 移除了 {@code and()} 和 {@code authorizeRequests()}。
 * 网上绝大多数教程是 6.x 的写法（{@code http.authorizeRequests()...and()...}），
 * <b>在 7.x 上会直接编译不过或运行期报错</b>。7.x 只能用
 * {@code Customizer} 函数式风格，也就是下面每一行 {@code .xxx(c -> ...)} 的写法。
 */
@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** 业务错误码：与 {@code GlobalExceptionHandler} 的编号习惯保持一致。 */
    private static final int UNAUTHORIZED_CODE = 4010;
    private static final int FORBIDDEN_CODE = 4030;

    private final ObjectMapper objectMapper;

    /** 令牌解析器：兼容标准 Authorization 头与项目历史的自定义 token 头。 */
    private final CompositeBearerTokenResolver compositeBearerTokenResolver;

    public SecurityConfig(ObjectMapper objectMapper,
                          CompositeBearerTokenResolver compositeBearerTokenResolver) {
        this.objectMapper = objectMapper;
        this.compositeBearerTokenResolver = compositeBearerTokenResolver;
    }

    // ══════════════════════════════════════════════════════════════
    // 一、JWT 解码器：替代 JwtUtil.parseToken 的验签职责
    // ══════════════════════════════════════════════════════════════

    /**
     * 用与签发端相同的密钥构造 JWT 解码器。
     *
     * <h3>为什么必须显式指定 HS512</h3>
     * 本项目签发时用的是 {@code Jwts...signWith(key)}，而 JJWT 会
     * <b>按密钥长度自动选算法</b>：≥64 字节选 HS512、48~63 选 HS384、32~47 选 HS256。
     * 解码端如果只写 {@code withSecretKey(key)}，Nimbus 默认按 <b>HS256</b> 校验，
     * 于是"自己签的 token 自己验不过"——这类问题表现为一律 401，很难排查。
     * <p>
     * 所以这里从配置读算法（默认 HS512），保证签发/校验两边一致。
     *
     * <h3>为什么同时校验密钥长度</h3>
     * HMAC 算法对密钥长度有硬性要求（HS512 要求 ≥512 bit = 64 字节）。
     * 在启动时就检查，比等到第一个请求 500 要好——这是 fail-fast。
     *
     * @param secret   签名密钥，来自 {@code jwt.secret}
     * @param algName  算法名，来自 {@code jwt.algorithm}，默认 HS512
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret,
                                @Value("${jwt.algorithm:HS512}") String algName,
                                JwtBlacklistValidator jwtBlacklistValidator) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        MacAlgorithm macAlgorithm = switch (algName.toUpperCase()) {
            case "HS256" -> MacAlgorithm.HS256;
            case "HS384" -> MacAlgorithm.HS384;
            case "HS512" -> MacAlgorithm.HS512;
            default -> throw new IllegalStateException(
                    "不支持的 jwt.algorithm=" + algName + "，仅支持 HS256 / HS384 / HS512");
        };

        int requiredBytes = switch (macAlgorithm) {
            case HS256 -> 32;
            case HS384 -> 48;
            case HS512 -> 64;
            default -> 64;
        };
        if (keyBytes.length < requiredBytes) {
            // 配置写错时宁可启动失败，也不要带着不安全的密钥跑起来
            throw new IllegalStateException(String.format(
                    "jwt.secret 长度不足：%s 要求至少 %d 字节，当前只有 %d 字节。"
                            + "请用 `openssl rand -base64 64` 生成新密钥，并通过环境变量 JWT_SECRET 注入。",
                    macAlgorithm.name(), requiredBytes, keyBytes.length));
        }

        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA512");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(macAlgorithm)
                .build();

        /*
         * ★ 挂上黑名单校验器。
         *
         * 这一步是"迁移不能只看编译通过"的典型例子：原来手写的 TokenInterceptor
         * 除了验签，还负责查 Redis 黑名单（登出功能）。拦截器删掉后，
         * 登出接口依然在往 Redis 写黑名单，但没人读了 —— 表现为"登出后令牌仍然有效"。
         * 这种回归不会编译失败、不会抛异常，只能靠端到端测试发现。
         *
         * 把校验挂进解码器后，框架在解码阶段就会拒绝黑名单里的令牌，
         * 自动按认证失败处理（401），而且带上标准的 OAuth2Error 描述。
         *
         * 注意顺序：DelegatingOAuth2TokenValidator 会依次执行，
         * 默认的 JwtValidators.createDefault()（校验 exp/nbf）由 NimbusJwtDecoder.build()
         * 已经设置好；这里 setJwtValidator 会**替换**它，所以必须显式把默认校验器带上，
         * 否则过期时间就不再被检查了（这是个很容易漏的安全漏洞）。
         */
        OAuth2TokenValidator<Jwt> withDefaults = JwtValidators.createDefault();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withDefaults, jwtBlacklistValidator));

        log.info("JWT 解码器已就绪：算法={} 密钥长度={} 字节，已启用默认校验(exp/nbf) + 黑名单校验",
                macAlgorithm.name(), keyBytes.length);
        return decoder;
    }

    // ══════════════════════════════════════════════════════════════
    // 二、过滤器链：替代整个 TokenInterceptor
    // ══════════════════════════════════════════════════════════════

    /**
     * 安全过滤器链。
     * <p>
     * 这个方法替代了原来的 {@code TokenInterceptor}（验签 + 黑名单 + 写 401）
     * 和 {@code WebConfig} 里的放行名单。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * CSRF：本项目的鉴权凭证是请求头里的 token，不是 Cookie，
                 * 浏览器不会自动携带，所以不存在 CSRF 攻击面。
                 * （如果哪天改成 Cookie 存 token，必须重新打开 CSRF 防护。）
                 */
                .csrf(CsrfConfigurer::disable)

                /*
                 * 无状态：JWT 自带状态，服务端不存 session。
                 * 不设这一条的话 Spring Security 会创建 HttpSession，
                 * 那就违背了"无状态"的初衷，也会让水平扩容变复杂。
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * 关掉表单登录和 HTTP Basic。
                 * 这两个是 Spring Security 的默认行为，不关的话：
                 *   · 未认证请求会被 302 重定向到 /login 页面，而不是返回 401 JSON；
                 *   · 浏览器可能弹出原生的用户名/密码对话框。
                 * 对前后端分离的 API 来说都必须关掉。
                 */
                .formLogin(FormLoginConfigurer::disable)
                .httpBasic(HttpBasicConfigurer::disable)

                /*
                 * 授权规则。顺序有意义：先放行，再兜底。
                 * 这里的路径必须是【真实映射路径】——原来 WebConfig 写成 "/user/login"，
                 * 而实际是 "/api/auth/login"（少了 /api），是死配置。
                 */
                .authorizeHttpRequests(auth -> auth
                        // 登录/注册：本身就是用来获取 token 的，必须放行
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        // 上传的封面图等静态资源：前端用 <img src> 直接引用，带不了 token
                        .requestMatchers("/uploads/**").permitAll()
                        // 接口文档
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 健康检查：运维探针用，需显式放行（Boot 默认只暴露 health）
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 其余一律要求已认证
                        .anyRequest().authenticated())

                /*
                 * 开启 JWT 校验：这一步替代了 TokenInterceptor 里"验签"的全部逻辑。
                 * 认证成功后，框架会把 JwtAuthenticationToken 写入 SecurityContextHolder。
                 *
                 * bearerTokenResolver：指定"从哪里取令牌"。
                 * 默认只认标准头 Authorization: Bearer xxx，而本项目历史上用的是自定义的
                 * `token: xxx` 裸头 —— 不注册这个解析器的话，令牌根本读不到，
                 * 表现为所有带合法 token 的请求都 401（详见 CompositeBearerTokenResolver 的注释）。
                 */
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(compositeBearerTokenResolver)
                        .jwt(Customizer.withDefaults())
                        // 自定义 401 响应体，保持与项目其他接口一致的 ApiResponse 结构
                        .authenticationEntryPoint(authenticationEntryPoint()))

                /*
                 * 403：已认证但权限不足（例如学生去审核学分）。
                 * 注意 401 和 403 的区别：
                 *   401 = 你没登录 / 凭证无效  → 应该去登录
                 *   403 = 你登录了，但没权限  → 登录也没用
                 * 原来手写实现里两者都返回 401，前端无法区分。
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

    /**
     * 保留给以后需要更细粒度规则时使用。
     * <p>
     * 说明：{@link #securityFilterChain} 里用 {@code permitAll()} 放行的路径，
     * 其顺序在 Spring Security 中是<b>按声明顺序匹配、先匹配先生效</b>，
     * 所以"具体路径写在宽泛路径之前"是关键，不要调换。
     */
    protected List<String> publicPaths() {
        return List.of("/api/auth/login", "/api/auth/register", "/uploads/**",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                "/actuator/health");
    }
}
