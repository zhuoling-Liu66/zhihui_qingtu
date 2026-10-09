package com.example.zhihuiqingtu.security;

import com.example.zhihuiqingtu.enums.UserRoleEnum;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 当前登录用户的统一读取入口。
 *
 * <p>身份由框架写入 {@link SecurityContextHolder}。本类收拢了对它的访问，
 * 使业务层只依赖"取当前学号/角色"这一语义，而不必各自处理类型判断、空值与
 * "学号存放在 JWT 的 sub 中"这一约定。
 *
 * <p><b>维护约束</b>
 * <ol>
 *   <li>不要用它替代 {@code @PreAuthorize}：授权规则应声明在方法注解上以便审计，
 *       本类只用于需要结合数据库实体归属判断的场景；</li>
 *   <li>{@link SecurityContextHolder} 默认不跨线程传递，引入 {@code @Async}
 *       或自建线程池后子线程中读取不到当前用户，需显式传递 {@code Authentication}。</li>
 * </ol>
 */
@Component
public class CurrentUser {

    /**
     * 取当前登录用户的学号/工号（JWT 的 {@code sub}）。
     *
     * @return 学号；未登录时返回 {@link Optional#empty()}
     */
    public Optional<String> userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        // JwtAuthenticationToken.getName() 返回的就是 JWT 的 sub（本项目存的是学号）
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    /**
     * 取当前登录用户的学号，未登录时抛异常。
     *
     * <p>需要登录的接口应使用本方法而非 {@link #userId()}：
     * 未登录时立即失败，避免静默返回空值而绕过后续的归属判断。
     *
     * @return 学号
     * @throws IllegalStateException 当前请求没有认证信息（通常意味着鉴权规则漏配了该路径）
     */
    public String requireUserId() {
        return userId().orElseThrow(() -> new IllegalStateException(
                "当前请求没有认证信息，但该接口要求登录。请检查 SecurityConfig 是否漏配了该路径的鉴权规则。"));
    }

    /**
     * 取当前登录用户的角色编码（JWT 自定义 claim {@code role}：1 学生 / 2 管理员 / 3 老师）。
     *
     * @return 角色编码；取不到时返回 {@link Optional#empty()}
     */
    public Optional<Integer> roleCode() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            Object role = jwt.getClaim("role");
            if (role instanceof Number number) {
                return Optional.of(number.intValue());
            }
        }
        return Optional.empty();
    }

    /**
     * 判断当前用户是否为指定角色。
     *
     * <p><b>能用 {@code @PreAuthorize} 表达的场景不要使用本方法</b>：
     * 声明式授权集中在方法注解上便于审计，散落在方法体内的判断容易遗漏。
     * 本方法用于需要结合数据库实体归属的复合规则。
     *
     * @param roleCode 角色编码（1 学生 / 2 管理员 / 3 老师）
     * @return true 表示当前用户是该角色
     */
    public boolean hasRole(int roleCode) {
        return roleCode().filter(code -> code == roleCode).isPresent();
    }

    /**
     * 判断当前用户是否为管理员或老师。
     *
     * <p>该判断在多个模块用于确定数据可见范围，集中于此可避免各 Service 重复实现
     * 而出现遗漏或编码写错，导致可见范围被悄然放宽。
     *
     * <p>注意其语义是"能看多大范围"，不代表"能否执行某操作"——
     * 后者通常还需校验实体归属（如是否为该活动的发布者）。
     *
     * @return true 表示当前登录用户是管理员或老师
     */
    public boolean isAdminOrTeacher() {
        return hasRole(UserRoleEnum.ADMIN.getCode()) || hasRole(UserRoleEnum.TEACHER.getCode());
    }

    /**
     * 取当前用户的全部权限名（供排障/日志使用）。
     *
     * @return 权限名列表
     */
    public java.util.List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return java.util.List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
