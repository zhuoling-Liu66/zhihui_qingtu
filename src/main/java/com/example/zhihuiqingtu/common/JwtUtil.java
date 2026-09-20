package com.example.zhihuiqingtu.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
 * JWT 令牌工具类，负责令牌的签发与解析。
 * <p>
 * 签名算法采用 HS256（HMAC-SHA256）：用一把只有服务端知道的密钥对令牌内容计算签名，
 * 客户端无法伪造；令牌里任何一个字被改动，签名就对不上，解析时会直接抛异常。

 * <p>
 * 令牌本质是三段 Base64 字符串用点号连接：header.payload.signature。
 * <p>
 * 本类需要读取 Spring 配置，因此注册成 Bean 而不是静态工具类（密码编码器同理，见
 * {@code config/PasswordConfig}）。
 */
@Component
public class JwtUtil {

    /**
     * 角色在令牌中的字段名。拦截器取角色时复用这个常量，避免两处字符串写不一致。
     */
    public static final String CLAIM_ROLE = "role";

    /**
     * 签名密钥，从配置 jwt.secret 读取。
     * HS256 要求密钥至少 32 字节，启动时会校验长度（见 {@link #init()}）。
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 令牌有效期（毫秒），从配置 jwt.expiration 读取，默认 12 小时。
     */
    @Value("${jwt.expiration:43200000}")
    private  long expiration;

    /**
     * 由密钥字符串转换出的签名密钥对象，只在启动时初始化一次。
     */
    private static SecretKey key;

    /**
     * Spring 创建 Bean 后自动执行：校验密钥长度并初始化签名密钥。
     * 配置写错时宁可启动直接失败，也不要等到运行时第一笔请求才报错。
     */
    @PostConstruct
    public void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足 32 字节，HS256 要求密钥至少 256 位，请检查配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param userId 用户学号/工号，存进令牌的 subject 字段
     * @param role   用户角色编码（1 学生 / 2 管理员 / 3 老师），存进自定义字段
     * @return JWT 字符串
     */
    public  String generateToken(String userId, Integer role) {
        Date now = new Date();
        return Jwts.builder()
                // jti（JWT ID）：令牌的唯一标识。
                // 【为什么需要它】JWT 是无状态的，服务端不保存任何东西，所以"登出"在协议层面
                // 是做不到的 —— 签出去的令牌在过期前一直有效。要做黑名单，就必须有一个能唯一定位
                // "这一个令牌"的东西，jti 就是为此设计的标准字段（而不是拿整串 token 当 key，
                // 那样 key 太长，且令牌内容变了就是另一条记录）。
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * @param token JWT 字符串
     * @return 令牌载荷，可从中取出学号和角色等字段
     * @throws JwtException 令牌过期、签名不合法或格式错误时抛出，由调用方统一捕获处理
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从令牌中取出 {@code jti}（唯一标识），用于登录黑名单。
     * <p>
     * 注意：本方法是后加的，所以<b>在加它之前签发的旧令牌里没有 jti</b>，会返回 null。
     * 生产环境做这种改造要考虑到这一点（本项目改完重启，旧 token 直接过期即可）。
     *
     * @param token JWT 字符串
     * @return 令牌唯一标识；旧格式令牌返回 null
     */
    public static String getJti(String token) {
        return parseToken(token).getId();
    }

    /**
     * 返回令牌距过期还剩多少秒。
     * <p>
     * 这是"用 TTL 代替删除"的关键：黑名单 key 的存活时间就设成令牌自己的剩余寿命。
     * 令牌一旦过期，签名校验本身就会失败，黑名单里再留着它就是浪费内存 ——
     * <b>让 Redis 自动清理，而不是写定时任务去删。</b>
     *
     * @param token JWT 字符串
     * @return 剩余秒数；已过期返回 0
     */
    public static long getRemainingSeconds(String token) {
        Date expiration = parseToken(token).getExpiration();
        return Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
    }

    /**
     * 从令牌中取出用户学号/工号。
     *
     * @param token JWT 字符串
     * @return 用户学号/工号
     */
    public String getUserId(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从令牌中取出用户角色编码。
     *
     * @param token JWT 字符串
     * @return 角色编码，若令牌中没有该字段则为 null
     */
    public Integer getRole(String token) {
        return parseToken(token).get(CLAIM_ROLE, Integer.class);
    }
}
