package com.example.zhihuiqingtu.ai.service;

import com.example.zhihuiqingtu.ai.config.AiProperties;
import com.example.zhihuiqingtu.ai.vo.QuotaVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 限流、每日 token 配额、全局降级开关。
 *
 * <p>实现直接复用项目既有的 Redis key 风格（{@code 业务域:对象:标识}），
 * 与登录锁定 {@code auth:login:fail:{studentNo}} 保持一致。
 *
 * <p><b>降级原则</b>：Redis 挂了不能把 AI 也拖死。所有 Redis 操作都吞掉异常并放行，
 * 因为限流/配额是"成本保护"而不是"安全边界"——真正的安全边界是 JWT 与归属校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private static final String RL_KEY_PREFIX = "ai:rl:chat:";
    private static final String QUOTA_KEY_PREFIX = "ai:quota:token:";
    private static final String DEGRADE_KEY = "ai:degrade:global";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties properties;

    /**
     * 滑动窗口（简化版固定窗口）限流：每分钟最多 N 次。
     *
     * @param userId 当前用户
     * @throws BusinessException 429 触发频率限制
     */
    public void checkRateLimit(String userId) {
        String key = RL_KEY_PREFIX + userId;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > properties.getRatelimit().getPerMinute()) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "发得太快了，请稍后再试（每分钟最多 "
                                + properties.getRatelimit().getPerMinute() + " 次）");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI 限流检查失败（Redis 不可用？），本次放行：{}", exception.getMessage());
        }
    }

    /**
     * 检查每日 token 配额，超限抛 429。
     */
    public void checkTokenQuota(String userId) {
        long used = usedTokens(userId);
        if (used >= properties.getQuota().getDailyTokens()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "今日 AI 额度已用完，明天再来");
        }
    }

    /**
     * 累加 token 用量。
     *
     * <p>用 {@code INCRBY} 且只在第一次设置过期时间：用量统计不需要精确事务，
     * 多算几十个 token 没有业务影响，少算反而会让配额形同虚设。
     */
    public void recordTokens(String userId, int tokens) {
        if (tokens <= 0) {
            return;
        }
        String key = quotaKey(userId);
        try {
            Long current = stringRedisTemplate.opsForValue().increment(key, tokens);
            if (current != null && current == tokens) {
                stringRedisTemplate.expire(key, ttlUntilTomorrow());
            }
        } catch (RuntimeException exception) {
            log.warn("AI token 用量记录失败：{}", exception.getMessage());
        }
    }

    /**
     * 读取今日已用 token。
     */
    public long usedTokens(String userId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(quotaKey(userId));
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException exception) {
            // 解析失败也当 0：宁可多放行一次，也不要因脏数据把用户永久锁死
            return 0L;
        }
    }

    /**
     * 全局降级开关：上游失败率过高时打开，期间所有对话直接走规则推荐。
     */
    public boolean isDegraded() {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(DEGRADE_KEY));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void markDegraded() {
        try {
            stringRedisTemplate.opsForValue().set(DEGRADE_KEY, "1", Duration.ofMinutes(5));
            log.warn("AI 上游异常率过高，已开启全局降级 5 分钟");
        } catch (RuntimeException exception) {
            log.warn("开启全局降级失败：{}", exception.getMessage());
        }
    }

    /**
     * 额度信息（给前端展示）。
     */
    public QuotaVO quota(String userId) {
        long limit = properties.getQuota().getDailyTokens();
        long used = usedTokens(userId);
        return QuotaVO.builder()
                .dailyTokenLimit(limit)
                .usedTokens(used)
                .remainingTokens(Math.max(0, limit - used))
                .resetAt(LocalDate.now().plusDays(1).atStartOfDay())
                .requestsPerMinute(properties.getRatelimit().getPerMinute())
                .enabled(properties.isEnabled())
                .build();
    }

    private String quotaKey(String userId) {
        return QUOTA_KEY_PREFIX + userId + ":" + LocalDate.now();
    }

    private Duration ttlUntilTomorrow() {
        Duration duration = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
        // 加一小时缓冲：跨零点时不会因时钟微小偏差提前过期
        return duration.plusHours(1);
    }

    /** 供测试/运维查看当前窗口计数。 */
    public long currentWindowCount(String userId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(RL_KEY_PREFIX + userId);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /** 单位为秒，保留给需要显式设置的场景。 */
    public void expire(String key, long seconds) {
        stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }
}
