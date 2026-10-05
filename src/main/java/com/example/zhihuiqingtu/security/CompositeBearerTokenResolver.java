package com.example.zhihuiqingtu.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从请求中解析 JWT —— 同时兼容「标准头」和「项目历史自定义头」。
 *
 * <h2>为什么需要这个类</h2>
 * 换成 Spring Security 的 OAuth2 Resource Server 之后，JWT 校验过滤器
 * （{@code BearerTokenAuthenticationFilter}）默认<b>只认标准格式</b>：
 * <pre>
 * Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
 * </pre>
 * 而本项目历史上（以及当前前端）用的是<b>自定义裸 token 头</b>：
 * <pre>
 * token: eyJhbGciOiJIUzUxMiJ9...
 * </pre>
 *
 * 这个差异的后果非常隐蔽：<b>令牌压根没被读取，认证上下文为空，
 * 所有带合法 token 的请求都返回 401</b>，而登录接口却是正常的
 * （因为登录不需要 token）。现象看起来像"登录成功了但什么都没权限"，
 * 极容易误判成"密钥配错了"或"算法不匹配"。
 *
 * <h2>Spring Security 提供的扩展点</h2>
 * 框架没有强制你用标准头，而是留了 {@link BearerTokenResolver} 接口：
 * <pre>
 * .oauth2ResourceServer(o -&gt; o.bearerTokenResolver(myResolver).jwt(...))
 * </pre>
 * 这就是"用框架"的正确姿势——<b>遇到特殊情况时找扩展点，而不是自己另写一套过滤器</b>。
 *
 * <h2>本类的策略：两者都支持</h2>
 * <ol>
 *   <li>优先标准格式 {@code Authorization: Bearer xxx} —— 推荐用法，
 *       与 Swagger UI、Postman、网关、第三方客户端都兼容；</li>
 *   <li>回退到历史格式 {@code token: xxx} —— 保证平滑迁移，
 *       已发布的前端/脚本不会因为这次改造直接失效。</li>
 * </ol>
 * 前端已同步改为标准格式；等确认没有旧客户端在用之后，
 * 把 {@code campus.security.legacy-token-header} 置空即可关掉兼容分支。
 */
@Component
public class CompositeBearerTokenResolver implements BearerTokenResolver {

    /** 标准格式（Authorization: Bearer xxx）的解析，直接复用框架默认实现。 */
    private final DefaultBearerTokenResolver standardResolver = new DefaultBearerTokenResolver();

    /** 历史自定义头名，配空字符串表示不再兼容。 */
    private final String legacyHeaderName;

    public CompositeBearerTokenResolver(
            @Value("${campus.security.legacy-token-header:token}") String legacyHeaderName) {
        this.legacyHeaderName = legacyHeaderName;
    }

    @Override
    public String resolve(jakarta.servlet.http.HttpServletRequest request) {
        // ① 标准格式优先
        String standard = standardResolver.resolve(request);
        if (StringUtils.hasText(standard)) {
            return standard;
        }

        // ② 回退：历史自定义头，值是【裸 token】（不带 "Bearer " 前缀）
        if (StringUtils.hasText(legacyHeaderName)) {
            String legacy = request.getHeader(legacyHeaderName);
            if (StringUtils.hasText(legacy)) {
                return legacy.trim();
            }
        }
        return null;
    }
}
