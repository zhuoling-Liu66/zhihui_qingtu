package com.example.zhihuiqingtu.interceptor;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.JwtUtil;
import com.example.zhihuiqingtu.service.impl.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录令牌拦截器。
 * <p>
 * 除了登录、注册等少数放行路径外，所有请求都必须带 {@code token} 请求头，
 * 校验不通过就拦下来。
 * <p>
 * <b>关于"拦下来"要做什么：</b>
 * {@code preHandle} 返回 {@code false} 只是告诉 Spring MVC "别再往下走了"，
 * 它<b>不会自动设置状态码，也不会写响应体</b>。所以如果只写 {@code return false}，
 * 客户端收到的是一个 {@code 200} + {@code Content-Length: 0} 的空响应 ——
 * 看起来像"接口返回了空数据"，而不像"没登录"。
 * <p>
 * 因此这里统一走 {@link #writeUnauthorized}，显式写出
 * {@code 401} 状态码和与项目其他接口一致的 JSON 结构：
 * <pre>
 * { "code": 4010, "message": "未登录，请先登录", "data": null }
 * </pre>
 * 这样前端既能用 HTTP 状态码区分"未授权"，也能用 {@code code} 字段
 * 和其他业务错误保持同一套解析逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    /**
     * 鉴权失败的业务错误码。
     * <p>
     * 沿用 {@code GlobalExceptionHandler} 里的编号习惯（4000 参数校验、
     * 4040 资源不存在、5000 系统异常），这里用 4010 表示未授权。
     */
    private static final int UNAUTHORIZED_CODE = 4010;

    /**
     * 用于把响应对象写成 JSON。
     * <p>
     * 注意是 Jackson 3 的 {@code tools.jackson.databind.ObjectMapper}
     * （Spring Boot 4 的默认 JSON 库），不是教程里常见的
     * {@code com.fasterxml.jackson} 那个。
     */
    private final ObjectMapper objectMapper;

    /**
     * 令牌黑名单（Redis），用于支持"登出"。
     * <p>
     * JWT 是无状态的：服务端签发后不保存任何东西，所以"登出"在协议层面做不到 ——
     * 前端删掉本地 token 只是自欺欺人，那个 token 在服务端看来依然完全有效。
     * 解决办法就是服务端记一份"已作废令牌"的名单，这里多查一步。
     */
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 获得url
        String url = request.getRequestURL().toString();
        // 判断是否包含/login和/register
        if (url.contains("/login") || url.contains("/register")) {
            return true;
        }

        // 是否包含token
        String token = request.getHeader("token");
        if (token == null || token.isEmpty()) {
            log.info("token is null");
            writeUnauthorized(response, "未登录，请先登录");
            return false;
        }

        // 用Jwtutils解析token
        try {
            JwtUtil.parseToken(token);
        } catch (Exception e) {
            log.info("token 解析失败: {}", e.getMessage());
            writeUnauthorized(response, "登录已过期或令牌无效，请重新登录");
            return false;
        }

        // 解析通过还不够，还要查一次黑名单（登出后令牌虽然签名有效，但已被主动作废）。
        // 这就是"能登出"的代价：每个请求多一次 Redis 查询。
        if (tokenBlacklistService.isBlacklisted(token)) {
            log.info("token 已登出（命中黑名单）");
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }
        return true;
    }

    /**
     * 写出 401 未授权响应。
     * <p>
     * 三件事缺一不可：状态码、Content-Type（否则浏览器/前端可能按文本处理，
     * 中文也会乱码）、响应体。
     *
     * @param response HTTP 响应
     * @param message  给前端看的提示语
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.failure(UNAUTHORIZED_CODE, message)));
    }
}
