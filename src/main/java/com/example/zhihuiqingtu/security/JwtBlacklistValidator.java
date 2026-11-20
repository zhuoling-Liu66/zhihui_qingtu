package com.example.zhihuiqingtu.security;

import com.example.zhihuiqingtu.security.support.TokenBlacklistService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 令牌黑名单校验器：校验令牌是否已在登出时被作废。
 *
 * <p>作为 {@link OAuth2TokenValidator} 挂载到 JWT 解码器上，使校验成为框架解码流程的一部分：
 * 校验失败由框架按认证失败处理（401），无需额外的过滤器或响应编码，失败原因以
 * {@link OAuth2Error} 标准化输出。
 *
 * <p><b>维护约束</b>：本类是黑名单的唯一消费方。若移除或不再注册，
 * 登出接口仍会写入 Redis 黑名单但无人读取，表现为"登出后令牌依然有效"——
 * 该回归不会编译失败或抛异常，需要端到端测试覆盖
 * （见 {@code docs/api-test/redis-verify.mjs}）。
 */
@Component
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {

    /** 校验失败时的错误码，遵循 OAuth2 规范惯例。 */
    private static final String ERROR_CODE = "invalid_token";

    private final TokenBlacklistService tokenBlacklistService;

    public JwtBlacklistValidator(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (tokenBlacklistService.isBlacklisted(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE,
                    "令牌已登出（命中黑名单）",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
