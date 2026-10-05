package com.example.zhihuiqingtu.security;

import com.example.zhihuiqingtu.service.impl.TokenBlacklistService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * JWT 黑名单校验器 —— 让"登出"在无状态 JWT 下真正生效。
 *
 * <h2>为什么需要这个类（一个真实的迁移教训）</h2>
 * 项目原来支持登出，做法是：登出时把令牌的 {@code jti} 写进 Redis 黑名单
 * （key = {@code auth:token:blacklist:{jti}}，TTL = 令牌剩余寿命），
 * 然后由 <b>手写的 {@code TokenInterceptor}</b> 在每个请求里查一次黑名单。
 *
 * 迁移到 Spring Security 时，拦截器被删掉了 —— 于是出现一个**很隐蔽的功能回归**：
 * 登出接口依然能正常写入黑名单（Redis 里 key 确实存在、TTL 也对），
 * <b>但没有任何代码再去读它</b>，于是"登出后令牌依然有效"。
 * 这类问题不会报错、不会编译失败，只能靠端到端测试发现
 * （本项目正是靠 {@code docs/api-test/redis-verify.mjs} 的 §8 断言抓到的）。
 *
 * <h2>为什么用 OAuth2TokenValidator 而不是再写一个过滤器</h2>
 * Spring Security 允许把自定义校验挂进 JWT 解码器：
 * <pre>
 * NimbusJwtDecoder decoder = ...build();
 * decoder.setJwtValidator(new DelegatingOAuth2TokenValidator&lt;&gt;(...validators));
 * </pre>
 * 这样校验是框架解码流程的一部分：
 * <ul>
 *   <li>令牌不合法时框架自动按"认证失败"处理（返回 401），不需要自己写响应；</li>
 *   <li>不需要新增过滤器、不需要关心过滤器顺序；</li>
 *   <li>校验失败的原因可以标准化成 {@link OAuth2Error}。</li>
 * </ul>
 * 这正是"删掉手写组件之后，把它的职责迁到框架扩展点上"的做法。
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
