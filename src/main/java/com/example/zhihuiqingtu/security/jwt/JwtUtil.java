package com.example.zhihuiqingtu.security.jwt;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import com.example.zhihuiqingtu.config.SecurityConfig;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * JWT 令牌签发。
 *
 * <p><b>职责边界</b>：本类只负责<b>签发</b>。验签、校验有效期、校验登出黑名单
 * 由 Spring Security 的过滤器链负责（见 {@code config/SecurityConfig} 的 {@code JwtDecoder}），
 * 身份读取见 {@code security/CurrentUser}。
 * 不提供解析方法，是为了避免出现"绕过框架自己解析 token 做校验"的用法。
 *
 * <p><b>签名参数来自 {@link JwtProperties}</b>：算法与密钥必须与校验端完全一致，
 * 因此两端共用同一个配置对象，不各自读取配置项。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /**
     * 角色在令牌中的 claim 名。
     *
     * <p>必须与 {@code security/CurrentUser#roleCode()} 读取的 claim 名一致，
     * 双方都引用本常量以避免两处硬编码字符串写不一致。
     */
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    /**
     * 签发令牌。
     *
     * <p>令牌包含：{@code jti}（唯一标识，供登出黑名单使用）、{@code sub}（学号）、
     * {@code role}（角色编码）、{@code iat}/{@code exp}。
     *
     * @param userId 用户学号/工号，写入 subject
     * @param role   角色编码（1 学生 / 2 管理员 / 3 老师 / 4 运营人员）
     * @return JWT 字符串
     */
    public String generateToken(String userId, Integer role) {
        Date now = new Date();
        return Jwts.builder()
                // jti 是登出功能的前提：JWT 无状态，要作废单个令牌必须能唯一定位它
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtProperties.expiration()))
                // 必须走带算法参数的重载：signWith(key) 会按密钥长度自动选算法，
                // 那样签发端算法就不受配置控制，可能与校验端不一致
                .signWith(jwtProperties.secretKey(), jwtProperties.resolvedAlgorithm().toJjwtAlgorithm())
                .compact();
    }
}
