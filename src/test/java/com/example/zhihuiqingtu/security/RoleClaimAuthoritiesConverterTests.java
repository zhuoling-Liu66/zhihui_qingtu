package com.example.zhihuiqingtu.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoleClaimAuthoritiesConverter} 的映射测试。
 *
 * <p><b>为什么要专门测这个类</b>：它修复的是一个"静默失效"缺陷——
 * 框架默认的 {@code JwtGrantedAuthoritiesConverter} 只识别字符串与集合类型的 claim，
 * 而本项目签发端写入的 {@code role} 是<b>数字</b>，于是权限集合恒为空，
 * 表现为所有 {@code hasRole(...)} 判断永远拒绝，<b>且不报任何错</b>。
 *
 * <p>因此这里重点覆盖 claim 的<b>各种取值形态</b>：
 * 数字（当前签发格式）、字符串（早期令牌可能的格式）、越界编码、缺失、类型异常。
 * 这些分支一旦漏测，就会出现"改了没生效、但看不出来"的情况。
 *
 * <p>纯逻辑测试，不启动 Spring 容器。
 */
class RoleClaimAuthoritiesConverterTests {

    private final RoleClaimAuthoritiesConverter converter = new RoleClaimAuthoritiesConverter();

    /** 构造一个只带指定 role claim 的令牌。 */
    private static Jwt jwtWithRole(Object roleValue) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "HS512")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("T0101");
        if (roleValue != null) {
            builder.claim("role", roleValue);
        }
        return builder.build();
    }

    private static List<String> authorityNames(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void 数字角色编码映射为带前缀的权限名() {
        // 当前签发端的真实格式：Integer
        assertEquals(List.of("ROLE_STUDENT"), authorityNames(converter.convert(jwtWithRole(1))));
        assertEquals(List.of("ROLE_ADMIN"), authorityNames(converter.convert(jwtWithRole(2))));
        assertEquals(List.of("ROLE_TEACHER"), authorityNames(converter.convert(jwtWithRole(3))));
    }

    @Test
    void 前缀必须是ROLE_否则hasRole永远不匹配() {
        // hasRole("ADMIN") 实际要求权限名为 ROLE_ADMIN（框架自动补前缀）。
        // 这条断言把"前缀约定"钉死，避免有人改成裸的 ADMIN 导致 hasRole 静默失效。
        assertTrue(authorityNames(converter.convert(jwtWithRole(2))).contains("ROLE_ADMIN"));
    }

    @Test
    void 字符串形式的角色编码同样可识别() {
        // 兼容早期令牌或 JSON 序列化差异导致的字符串形态
        assertEquals(List.of("ROLE_ADMIN"), authorityNames(converter.convert(jwtWithRole("2"))));
        // 带空白也应能解析
        assertEquals(List.of("ROLE_ADMIN"), authorityNames(converter.convert(jwtWithRole(" 2 "))));
    }

    @Test
    void 其他数字类型也可识别() {
        // JSON 数字反序列化后可能是 Long
        assertEquals(List.of("ROLE_ADMIN"), authorityNames(converter.convert(jwtWithRole(2L))));
    }

    @Test
    void 未知编码返回空权限而不是抛异常() {
        // 认证已成功，角色无法识别应表现为"无权限被拒"，而不是让整个认证流程崩溃
        assertTrue(converter.convert(jwtWithRole(99)).isEmpty());
        assertTrue(converter.convert(jwtWithRole(0)).isEmpty());
        assertTrue(converter.convert(jwtWithRole(-1)).isEmpty());
    }

    @Test
    void claim缺失返回空权限() {
        assertTrue(converter.convert(jwtWithRole(null)).isEmpty());
    }

    @Test
    void claim类型异常返回空权限() {
        assertTrue(converter.convert(jwtWithRole(Map.of("nested", "value"))).isEmpty());
        assertTrue(converter.convert(jwtWithRole(List.of(1, 2))).isEmpty());
        assertTrue(converter.convert(jwtWithRole("not-a-number")).isEmpty());
    }
}
