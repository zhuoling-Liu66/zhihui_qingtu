package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.security.jwt.JwtProperties;
import com.example.zhihuiqingtu.security.support.UserRevocationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 按用户撤销标记的写入与读取。
 *
 * <p>重点覆盖两个容易静默出错的点：
 * TTL 必须等于 JWT 有效期（否则标记先于旧令牌过期，禁用"到期自动解除"）；
 * 值必须是 epoch 秒（校验器按 iat 与它比较）。
 */
class UserRevocationServiceTests {

    private static final String TEST_SECRET =
            "zhihuiqingtu-revocation-test-secret-0123456789-0123456789-0123456789";
    private static final long EXPIRATION_MILLIS = 3_600_000L;

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final UserRevocationService userRevocationService = new UserRevocationService(
            stringRedisTemplate, new JwtProperties(TEST_SECRET, "HS512", EXPIRATION_MILLIS));

    @Test
    void revokeWritesEpochSecondWithJwtExpirationTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        long before = Instant.now().getEpochSecond();

        userRevocationService.revoke("S0001");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("auth:user:revoked:S0001"), valueCaptor.capture(),
                eq(Duration.ofMillis(EXPIRATION_MILLIS)));
        long revokedAt = Long.parseLong(valueCaptor.getValue());
        long after = Instant.now().getEpochSecond();
        assertTrue(revokedAt >= before && revokedAt <= after,
                "撤销时刻应为当前 epoch 秒，实际=" + revokedAt);
    }

    @Test
    void revokedAtReturnsStoredEpochSecond() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:user:revoked:S0001")).thenReturn("1700000000");

        assertEquals(1700000000L, userRevocationService.revokedAtEpochSecond("S0001"));
    }

    @Test
    void revokedAtIsNullWhenNoMarker() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:user:revoked:S0001")).thenReturn(null);

        assertNull(userRevocationService.revokedAtEpochSecond("S0001"));
    }

    @Test
    void corruptedMarkerIsTreatedAsNotRevoked() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:user:revoked:S0001")).thenReturn("not-a-number");

        assertNull(userRevocationService.revokedAtEpochSecond("S0001"));
    }

    @Test
    void blankStudentNoNeverTouchesRedis() {
        assertNull(userRevocationService.revokedAtEpochSecond(null));
        assertNull(userRevocationService.revokedAtEpochSecond(" "));
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void revokeOverwritesPreviousMarker() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        userRevocationService.revoke("S0001");
        userRevocationService.revoke("S0001");

        // set 是无条件覆盖：重复禁用只会刷新撤销时刻，语义更严格，不会放行旧令牌
        verify(valueOperations, org.mockito.Mockito.times(2))
                .set(eq("auth:user:revoked:S0001"), anyString(), eq(Duration.ofMillis(EXPIRATION_MILLIS)));
    }
}
