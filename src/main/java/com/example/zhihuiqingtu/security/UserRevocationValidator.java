package com.example.zhihuiqingtu.security;

import com.example.zhihuiqingtu.security.support.UserRevocationService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import com.example.zhihuiqingtu.config.SecurityConfig;

/**
 * 按用户撤销校验器：账号被禁用/注销后，其已签发令牌立即失效。
 *
 * <p>与 {@link JwtBlacklistValidator}（按 jti 的登出黑名单）互补：
 * 黑名单作废单个令牌，本校验器按用户作废"撤销时刻之前签发的所有令牌"。
 * 二者都挂在 JWT 解码器上（见 {@code config/SecurityConfig}），
 * 校验失败由框架按认证失败处理（401）。
 *
 * <p><b>比较规则</b>：{@code iat <= revokedAt} 即拒绝。取等号是保守处理——
 * epoch 秒精度下无法区分同一秒内的先后，宁可让"禁用同一秒签发的令牌"也失效，
 * 也不留一秒的绕过窗口。被误伤的用户重新登录即可。
 *
 * <p><b>缺少 iat 的令牌一律拒绝</b>（仅当存在撤销标记时）：
 * 无法证明它签发于撤销之后，就不能放行。本项目签发的令牌都带 iat，
 * 该分支只防御手工构造的异常令牌。
 */
@Component
public class UserRevocationValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ERROR_CODE = "invalid_token";

    private final UserRevocationService userRevocationService;

    public UserRevocationValidator(UserRevocationService userRevocationService) {
        this.userRevocationService = userRevocationService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long revokedAt = userRevocationService.revokedAtEpochSecond(jwt.getSubject());
        if (revokedAt == null) {
            return OAuth2TokenValidatorResult.success();
        }
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null || issuedAt.getEpochSecond() <= revokedAt) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE,
                    "账号已被禁用或注销，请重新登录",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
