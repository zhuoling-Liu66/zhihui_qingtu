package com.example.zhihuiqingtu.security;

import com.example.zhihuiqingtu.security.support.UserRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按用户撤销校验器的 iat 比较规则。
 *
 * <p>安全关键：{@code iat <= revokedAt} 必须拒绝（含等号，堵住同一秒的绕过窗口）；
 * 有撤销标记但令牌缺 iat 时也必须拒绝（无法证明签发于撤销之后）。
 */
class UserRevocationValidatorTests {

    private static final String SUBJECT = "S0001";
    private static final long REVOKED_AT = 1_700_000_000L;

    private final UserRevocationService userRevocationService = mock(UserRevocationService.class);
    private final UserRevocationValidator validator = new UserRevocationValidator(userRevocationService);

    @Test
    void tokenWithoutRevocationMarkerPasses() {
        when(userRevocationService.revokedAtEpochSecond(SUBJECT)).thenReturn(null);

        assertFalse(validate(tokenIssuedAt(REVOKED_AT - 100)).hasErrors());
    }

    @Test
    void tokenIssuedBeforeRevocationIsRejected() {
        when(userRevocationService.revokedAtEpochSecond(SUBJECT)).thenReturn(REVOKED_AT);

        OAuth2TokenValidatorResult result = validate(tokenIssuedAt(REVOKED_AT - 1));

        assertTrue(result.hasErrors());
    }

    @Test
    void tokenIssuedAtSameSecondAsRevocationIsRejected() {
        when(userRevocationService.revokedAtEpochSecond(SUBJECT)).thenReturn(REVOKED_AT);

        // epoch 秒精度下无法区分同一秒内的先后，保守拒绝
        assertTrue(validate(tokenIssuedAt(REVOKED_AT)).hasErrors());
    }

    @Test
    void tokenIssuedAfterRevocationPasses() {
        when(userRevocationService.revokedAtEpochSecond(SUBJECT)).thenReturn(REVOKED_AT);

        assertFalse(validate(tokenIssuedAt(REVOKED_AT + 1)).hasErrors());
    }

    @Test
    void tokenWithoutIatIsRejectedWhenMarkerExists() {
        when(userRevocationService.revokedAtEpochSecond(SUBJECT)).thenReturn(REVOKED_AT);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS512")
                .subject(SUBJECT)
                .expiresAt(Instant.ofEpochSecond(REVOKED_AT + 3600))
                .build();

        assertTrue(validate(jwt).hasErrors());
    }

    @Test
    void tokenWithoutIatPassesWhenNoMarker() {
        when(userRevocationService.revokedAtEpochSecond(SUBJECT)).thenReturn(null);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS512")
                .subject(SUBJECT)
                .expiresAt(Instant.ofEpochSecond(REVOKED_AT + 3600))
                .build();

        assertFalse(validate(jwt).hasErrors());
    }

    private OAuth2TokenValidatorResult validate(Jwt jwt) {
        return validator.validate(jwt);
    }

    private Jwt tokenIssuedAt(long epochSecond) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS512")
                .subject(SUBJECT)
                .issuedAt(Instant.ofEpochSecond(epochSecond))
                .expiresAt(Instant.ofEpochSecond(epochSecond + 3600))
                .build();
    }
}
