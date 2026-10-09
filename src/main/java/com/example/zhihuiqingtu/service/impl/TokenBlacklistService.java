package com.example.zhihuiqingtu.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 令牌黑名单：为无状态 JWT 提供登出能力。
 *
 * <p>JWT 签发后服务端不保存状态，因此无法在过期前撤销。本类以 Redis 记录已作废令牌：
 * <pre>
 * key   auth:token:blacklist:{jti}
 * value "1"
 * TTL   = 该令牌的剩余有效期
 * </pre>
 *
 * <p><b>TTL 必须取令牌剩余寿命</b>，而非固定值或永久：令牌一旦过期，签名校验本身即会失败，
 * 再保留黑名单条目只是占用内存。以此方式无需任何定时清理任务。
 *
 * <p><b>代价</b>：每个请求增加一次 Redis 查询，JWT 原本"无需查询存储"的优势被削弱。
 * 若业务不需要主动登出，更合适的方案是缩短有效期（如 15 分钟）并配合刷新令牌。
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
     * 将令牌加入黑名单。
     *
     * <p>剩余寿命不足时跳过写入（见 {@link #blacklist(String, Instant)}）。
     *
     * @param jwt 已解析的 JWT
     */
    public void blacklist(Jwt jwt) {
        blacklist(jwt.getId(), jwt.getExpiresAt());
    }

    /**
     * 将令牌加入黑名单。
     *
     * <p>不写入的两种情况：缺少 {@code jti}（无法作为 key）、已过期或无法确定剩余寿命
     * （此时签名与有效期校验已能拦截，写入只会造成无 TTL 的常驻 key）。
     *
     * @param jti       令牌唯一标识
     * @param expiresAt 令牌过期时间，用于计算 TTL
     */
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            log.warn("令牌缺少 jti，无法加入黑名单");
            return;
        }
        if (expiresAt == null) {
            log.warn("令牌没有 exp，无法确定黑名单 TTL，跳过 jti={}", jti);
            return;
        }
        long remainingSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (remainingSeconds <= 0) {
            log.debug("令牌已过期，无需加入黑名单 jti={}", jti);
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(blacklistKey(jti), "1", Duration.ofSeconds(remainingSeconds));
        log.info("令牌已加入黑名单 jti={} 剩余有效期={}s", jti, remainingSeconds);
    }

    /**
     * 判断令牌是否已被登出。
     *
     * @param jwt 已解析并验签的 JWT
     * @return true 表示该令牌已被撤销
     */
    public boolean isBlacklisted(Jwt jwt) {
        return isBlacklisted(jwt.getId());
    }

    /**
     * 按 {@code jti} 判断是否在黑名单中。
     *
     * @param jti 令牌唯一标识
     * @return true 表示已撤销；jti 为空时返回 false
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey(jti)));
    }
}
