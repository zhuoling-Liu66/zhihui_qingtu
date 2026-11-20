package com.example.zhihuiqingtu.security.support;

import com.example.zhihuiqingtu.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import com.example.zhihuiqingtu.security.UserRevocationValidator;

/**
 * 按用户撤销令牌：为"禁用/注销账号"提供立即生效的令牌作废能力。
 *
 * <p>登出黑名单（{@link TokenBlacklistService}）按 {@code jti} 作废<b>单个</b>令牌，
 * 但管理员禁用账号时并不知道该用户手里有多少个已签发令牌，无法逐个拉黑。
 * 因此这里换一个维度：按<b>用户</b>记录一个"撤销时间点"，
 * 凡签发时间（{@code iat}）不晚于该时间点的令牌一律视为已撤销：
 * <pre>
 * key   auth:user:revoked:{studentNo}
 * value 撤销时刻的 epoch 秒
 * TTL   = JWT 有效期（jwt.expiration）
 * </pre>
 *
 * <p><b>TTL 取 JWT 有效期即可覆盖所有旧令牌</b>：撤销时刻之前签发的令牌，
 * 其过期时间必然早于"撤销时刻 + 有效期"，届时签名链的 exp 校验自己就会拦截，
 * 标记到期自动消失，无需清理任务。
 *
 * <p><b>启用账号时不删除标记</b>：删除会让禁用前签发的旧令牌"复活"。
 * 保留标记后，旧令牌（iat ≤ 撤销时刻）继续被拒，重新登录拿到的新令牌
 * （iat &gt; 撤销时刻）正常放行——这正是"禁用立即生效、启用后需重新登录"的语义。
 *
 * <p>消费方是 {@code security/UserRevocationValidator}，挂在 JWT 解码器上，
 * 每个请求解码时检查。若只写不读，表现为"禁用后旧令牌依然有效"，需端到端测试覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRevocationService {

    private static final String REVOKED_PREFIX = "auth:user:revoked:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    private static String revokedKey(String studentNo) {
        return REVOKED_PREFIX + studentNo;
    }

    /**
     * 撤销该用户当前已签发的全部令牌（禁用/注销账号时调用）。
     *
     * <p>重复调用会刷新撤销时刻与 TTL：语义上等价于"此刻之前的令牌全部作废"，
     * 只会更严格，不会放行任何旧令牌。
     *
     * @param studentNo 学号/工号
     */
    public void revoke(String studentNo) {
        long nowSecond = Instant.now().getEpochSecond();
        stringRedisTemplate.opsForValue().set(
                revokedKey(studentNo),
                Long.toString(nowSecond),
                Duration.ofMillis(jwtProperties.expiration()));
        log.info("已按用户撤销令牌 studentNo={} revokedAt={}s", studentNo, nowSecond);
    }

    /**
     * 读取该用户的撤销时刻。
     *
     * @param studentNo 学号/工号
     * @return 撤销时刻的 epoch 秒；从未被撤销（或标记已过期）时返回 {@code null}
     */
    public Long revokedAtEpochSecond(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(revokedKey(studentNo));
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // 值被外部写坏时按"未撤销"处理并告警，而不是让所有请求 500。
            log.warn("用户撤销标记值非法 studentNo={} value={}", studentNo, value);
            return null;
        }
    }
}
