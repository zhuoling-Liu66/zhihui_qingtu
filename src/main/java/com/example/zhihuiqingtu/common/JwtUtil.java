package com.example.zhihuiqingtu.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌<b>签发</b>工具。
 *
 * <h2>职责范围（迁移后已收窄）</h2>
 * 本类现在**只负责签发**。原来的"解析与校验"职责已经交给 Spring Security：
 * <ul>
 *   <li>验签、检查 {@code exp}/{@code nbf}、检查黑名单
 *       → {@code config/SecurityConfig} 里的 {@code JwtDecoder}
 *       （由 {@code NimbusJwtDecoder} + {@code DelegatingOAuth2TokenValidator} 组成）；</li>
 *   <li>取身份（学号）→ 框架解析好的 {@code Jwt} 对象 / {@code security/CurrentUser}。</li>
 * </ul>
 * 所以 {@code parseToken} / {@code getJti} / {@code getRemainingSeconds} /
 * {@code getUserId} / {@code getRole} 这些手写解析方法**已被删除** ——
 * 留着它们只会让人误以为"项目还在自己解析 token"，也容易出现
 * "有人绕过框架用它们做校验"的危险用法。
 *
 * <h2>关于签名算法</h2>
 * 这里用 {@code signWith(key)}，JJWT 会<b>按密钥长度自动选择算法</b>：
 * <ul>
 *   <li>≥ 64 字节 → HS512</li>
 *   <li>48 ~ 63 字节 → HS384</li>
 *   <li>32 ~ 47 字节 → HS256</li>
 *   <li>&lt; 32 字节 → 直接抛 {@code WeakKeyException}</li>
 * </ul>
 * <b>校验端不会自动跟随</b>（NimbusJwtDecoder 默认按 HS256），
 * 所以 {@code application.properties} 里的 {@code jwt.algorithm} 必须与这里的密钥长度匹配，
 * 否则会出现"自己签的 token 自己验不过"，且症状是一律 401、极难排查。
 * {@code SecurityConfig} 会在密钥过短时直接启动失败来提前暴露这个问题。
 *
 * <p>
 * 本类需要读取 Spring 配置，因此注册成 Bean 而不是静态工具类
 * （密码编码器同理，见 {@code config/PasswordConfig}）。
 */
@Component
public class JwtUtil {

    /**
     * 角色在令牌中的字段名。
     * <p>
     * 这个名字必须与 {@code security/CurrentUser#roleCode()} 里读取的 claim 名一致；
     * 双方都引用本常量，避免两处字符串写不一致。
     */
    public static final String CLAIM_ROLE = "role";

    /**
     * 签名密钥，从配置 {@code jwt.secret} 读取。
     * <p>
     * 配置里**故意不写默认值**：没有环境变量时应用直接启动失败（fail-fast），
     * 免得带着一个仓库里公开的密钥静默运行——那等于任何人都能伪造令牌。
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 令牌有效期（毫秒），从配置 {@code jwt.expiration} 读取，默认 12 小时。
     */
    @Value("${jwt.expiration:43200000}")
    private long expiration;

    /**
     * 由密钥字符串转换出的签名密钥对象，只在启动时初始化一次。
     * <p>
     * 非 static：本类是单例 Bean，实例字段已足够；用 static 会导致
     * 同一 JVM 多上下文（测试、多 profile）时互相覆盖密钥。
     */
    private SecretKey key;

    /**
     * Spring 创建 Bean 后自动执行：校验密钥长度并初始化签名密钥。
     * <p>
     * 配置写错时宁可启动直接失败，也不要等到运行时第一笔请求才报错。
     */
    @PostConstruct
    public void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足 32 字节，HS256 及以上算法要求密钥至少 256 位，请检查配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param userId 用户学号/工号，存进令牌的 subject 字段（框架侧 {@code Jwt.getSubject()} 读它）
     * @param role   用户角色编码（1 学生 / 2 管理员 / 3 老师），存进自定义 claim
     * @return JWT 字符串
     */
    public String generateToken(String userId, Integer role) {
        Date now = new Date();
        return Jwts.builder()
                // jti（JWT ID）：令牌的唯一标识。
                // 【为什么需要它】JWT 无状态，服务端不保存东西，所以"登出"在协议层面做不到 ——
                // 签出去的令牌在过期前一直有效。要做黑名单，就必须有一个能唯一定位
                // "这一个令牌"的东西，jti 就是为此设计的标准字段
                // （而不是拿整串 token 当 key：太长，且令牌内容一变就是另一条记录）。
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }
}
