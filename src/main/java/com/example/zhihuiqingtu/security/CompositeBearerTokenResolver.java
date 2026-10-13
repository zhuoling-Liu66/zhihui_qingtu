package com.example.zhihuiqingtu.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 令牌解析器：同时支持标准 {@code Authorization: Bearer} 头与项目自定义的 {@code token} 裸头。
 *
 * <p><b>为什么需要自定义实现</b>：Spring Security 默认的 {@link DefaultBearerTokenResolver}
 * 只识别标准头。本项目历史接口使用自定义头，若不注册本解析器，框架将取不到令牌，
 * 认证上下文为空，<b>所有携带合法令牌的请求都会返回 401</b>；而登录接口不需要令牌，
 * 因此现象表现为"登录成功但所有接口无权限"，容易被误判为密钥或算法配置问题。
 *
 * <p>读取顺序：优先标准头，未命中时回退到自定义头。待确认无旧客户端依赖 {@code token} 头后，
 * 将配置项 {@code campus.security.legacy-token-header} 置空即可关闭兼容分支。
 *
 * @see org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
 */
@Component
public class CompositeBearerTokenResolver implements BearerTokenResolver {

    /** 标准头的解析逻辑，复用框架默认实现。 */
    private final DefaultBearerTokenResolver standardResolver = new DefaultBearerTokenResolver();

    /** 兼容用的历史头名；配置为空字符串表示不再兼容。 */
    private final String legacyHeaderName;

    public CompositeBearerTokenResolver(
            @Value("${campus.security.legacy-token-header:token}") String legacyHeaderName) {
        this.legacyHeaderName = legacyHeaderName;
    }

    @Override
    public String resolve(jakarta.servlet.http.HttpServletRequest request) {
        // 标准头优先
        String standard = standardResolver.resolve(request);
        if (StringUtils.hasText(standard)) {
            return standard;
        }

        // 回退到自定义头；其值为裸令牌，不含 "Bearer " 前缀
        if (StringUtils.hasText(legacyHeaderName)) {
            String legacy = request.getHeader(legacyHeaderName);
            if (StringUtils.hasText(legacy)) {
                return legacy.trim();
            }
        }
        return null;
    }
}
