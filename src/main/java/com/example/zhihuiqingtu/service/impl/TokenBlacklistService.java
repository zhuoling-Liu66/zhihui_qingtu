package com.example.zhihuiqingtu.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 令牌黑名单服务 —— 让无状态的 JWT 支持"登出"。
 *
 * <h2>为什么需要它</h2>
 * JWT 是<b>无状态</b>的：服务端签发后不保存任何东西，只靠签名验证真伪。
 * 好处是不用查库/查 session，天生适合前后端分离；代价是<b>签出去的令牌在过期前无法撤销</b>。
 * 用户点了"登出"，前端把本地 token 删掉了，但那个 token 在服务端看来依然完全有效 ——
 * 若被截获，仍能用满 12 小时（{@code jwt.expiration=43200000}）。
 *
 * <h2>方案：黑名单 + TTL</h2>
 * 登出时把令牌的 {@code jti} 记进 Redis，拦截器每次校验时多查一步。
 * <pre>
 * key   auth:token:blacklist:{jti}
 * value "1"
 * TTL   = 该令牌自己还剩多久过期
 * </pre>
 *
 * <h2>关键设计：TTL 用令牌的剩余寿命</h2>
 * 令牌一旦过期，签名校验本身就会失败，黑名单里再留着它就是纯粹的内存浪费。
 * 把 TTL 设成"令牌的剩余寿命"，就<b>不需要任何清理任务</b> ——
 * 这正是"用 TTL 代替删除"的典型用法，也是选 Redis 而不是 MySQL 做黑名单的理由。
 *
 * <h2>这个方案的代价（要知道自己在买什么）</h2>
 * JWT 原本的最大优点"不用查存储"被打破了：每次请求都要多一次 Redis 查询。
 * 这是用"一点性能 + 一点复杂度"换"能登出"。如果业务上完全不需要登出，
 * 更好的做法是<b>不引入黑名单</b>，而是把令牌有效期设短（如 15 分钟）+ 用 refresh token 续期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "auth:token:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    private static String blacklistKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }

    /**
     * 把令牌加入黑名单。
     * <p>
     * <b>剩余寿命为 0 时什么都不写</b>：令牌本来就已经过期了，写进去反而会（在极端情况下）
     * 变成一条没有 TTL 的常驻 key。对"已经过期的令牌"来说，签名校验这一关就已经把它挡住了。
     *
     * @param token 待失效的 JWT
     */
    public void blacklist(Jwt jwt) {
        blacklist(jwt.getId(), jwt.getExpiresAt());
    }

    /**
     * 把令牌加入黑名单（内部实现）。
     *
     * @param jti       令牌唯一标识（JWT 的 {@code jti}）
     * @param expiresAt 令牌过期时间，用于计算 TTL；为 null 时不写入（无法确定寿命）
     */
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            // 兼容加 jti 之前签发的旧令牌：没有唯一标识就没法进黑名单，只能等它自然过期
            log.warn("令牌缺少 jti，无法加入黑名单（可能是改造前签发的旧令牌）");
            return;
        }
        if (expiresAt == null) {
            log.warn("令牌没有 exp，无法确定黑名单 TTL，跳过 jti={}", jti);
            return;
        }
        long remainingSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (remainingSeconds <= 0) {
            // 已经过期的令牌不需要进黑名单：签名/有效期校验这一关就把它挡住了，
            // 写进去只会占内存（极端情况下还会落一条没有 TTL 的常驻 key）。
            log.debug("令牌已过期，无需加入黑名单 jti={}", jti);
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(blacklistKey(jti), "1", Duration.ofSeconds(remainingSeconds));
        log.info("令牌已加入黑名单 jti={} 剩余有效期={}s", jti, remainingSeconds);
    }

    /**
     * 判断令牌是否已被登出（或在黑名单中）。
     * <p>
     * 迁移说明：原来这个方法收的是原始 token 字符串、内部自己解析 jti；
     * 现在改收已经被 Spring Security <b>校验通过</b>的 {@link Jwt} 对象 ——
     * 因为调用它的是框架的令牌校验器（{@code JwtBlacklistValidator}），
     * 那时令牌已经解析好了，没必要再解析一次。
     *
     * @param jwt 已解析并验签的 JWT
     * @return true 表示该令牌已被撤销
     */
    public boolean isBlacklisted(Jwt jwt) {
        return isBlacklisted(jwt.getId());
    }

    /**
     * 按 jti 判断是否在黑名单中（内部实现）。
     *
     * @param jti 令牌唯一标识
     * @return true 表示已撤销；jti 为空时返回 false（无标识无从判断）
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey(jti)));
    }
}
