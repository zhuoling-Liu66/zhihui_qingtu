package com.example.zhihuiqingtu.security;

import com.example.zhihuiqingtu.user.enums.UserRoleEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.List;

/**
 * 把 JWT 的 {@code role} claim 转换为 {@link GrantedAuthority} 集合。
 *
 * <p><b>为什么不能直接用 {@link JwtGrantedAuthoritiesConverter}</b>：
 * 该实现只处理两种 claim 值类型（源码 {@code getAuthorities}）：
 * <ul>
 *   <li>{@code String} → 按分隔符切分；</li>
 *   <li>{@code Collection} → 逐项转字符串。</li>
 * </ul>
 * 其余类型（包括本项目的角色编码 {@code Integer}）会走
 * {@code return Collections.emptyList()}，即<b>静默返回空权限</b>。
 * 本项目签发端写入的是数字（{@code claim("role", Integer)}），
 * 因此套用默认实现会得到空 {@code GrantedAuthority} 集合，
 * 表现为 {@code hasRole(...)} 永远拒绝，且没有任何报错。
 *
 * <p><b>权限命名约定</b>：输出为 {@code ROLE_ + UserRoleEnum 名称}，
 * 例如角色码 2 映射为 {@code ROLE_ADMIN}。授权表达式必须与之匹配：
 * <ul>
 *   <li>{@code hasRole("ADMIN")} → 框架自动补 {@code ROLE_}，匹配 {@code ROLE_ADMIN} ✅</li>
 *   <li>{@code hasAuthority("ADMIN")} → 不加前缀，匹配 {@code ADMIN}，<b>不会命中</b> ❌</li>
 * </ul>
 */
@Slf4j
public class RoleClaimAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /** JWT 中承载角色编码的 claim 名。 */
    public static final String ROLE_CLAIM = "role";

    /** {@code hasRole("X")} 按 {@code ROLE_X} 匹配，故前缀必须与此一致。 */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * 读取 {@code role} claim 并转换为权限集合。
     *
     * <p>无法识别的取值（claim 缺失、类型不符、编码不在枚举内）一律返回<b>空集合</b>
     * 并记 warn 日志，而不是抛异常：认证本身（验签与身份解析）已经完成，
     * 角色缺失应当表现为"没有权限被拒绝"，而不是"认证过程崩溃"。
     *
     * @param jwt 已通过验签的令牌，不能为 null
     * @return 权限集合；无法识别时为空集合，绝不为 null
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Integer roleCode = resolveRoleCode(jwt.getClaim(ROLE_CLAIM));
        UserRoleEnum role = UserRoleEnum.fromCode(roleCode);
        if (role == null) {
            log.warn("JWT 的 {} claim 无法映射为角色（实际值: {}），本次认证不授予任何角色权限",
                    ROLE_CLAIM, jwt.getClaim(ROLE_CLAIM));
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
    }

    /**
     * 兼容数字与字符串两种 claim 表示。
     *
     * <p>JSON 里的数字在反序列化后可能是 {@code Integer} / {@code Long} 等不同包装类型，
     * 早期版本签发的令牌也可能是字符串（{@code "2"}），这里统一处理，
     * 避免因序列化差异导致权限静默丢失。
     *
     * @param raw claim 原始值
     * @return 角色编码；无法解析时返回 null
     */
    private Integer resolveRoleCode(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
