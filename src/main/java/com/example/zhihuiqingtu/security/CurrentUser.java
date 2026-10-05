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
 * 读取"当前登录用户"的统一入口。
 *
 * <h3>为什么要有这个类</h3>
 * 迁移到 Spring Security 之后，身份由框架写在 {@link SecurityContextHolder} 里。
 * 如果让每个 Service 都直接写
 * {@code SecurityContextHolder.getContext().getAuthentication()...}，
 * 会有三个问题：
 * <ol>
 *   <li>每处都要做类型判断和空值处理，重复且容易漏；</li>
 *   <li>"学号放在 JWT 的 sub 里"这个约定会散落到各处；</li>
 *   <li>将来改身份来源（比如换成 OAuth2 userinfo），要改很多地方。</li>
 * </ol>
 * 收在一个类里，业务层只依赖"给我当前学号"这一个语义。
 *
 * <h3>关于 SecurityContextHolder 也是 ThreadLocal —— 为什么这次可以放心用</h3>
 * 本项目放弃了"自己 new 一个 ThreadLocal 存身份"（那需要自己保证
 * {@code finally { remove(); }}，忘了就会在线程池复用线程时串数据）。
 * 而 {@link SecurityContextHolder} 是<b>框架提供的</b> ThreadLocal：
 * Spring Security 的 {@code SecurityContextPersistenceFilter} /
 * {@code SecurityContextHolderFilter} 会在每个请求开始时设置、请求结束时清理，
 * 责任在框架侧，不需要业务代码记得清理。
 * <p>
 * 这也解释了为什么"用框架"比"自己写"更安全：<b>不是因为框架代码写得更好，
 * 而是因为它把"必须清理"这件事变成了框架的职责，而不是每个开发者的记忆负担。</b>
 *
 * <h3>已知限制</h3>
 * {@code SecurityContextHolder} 默认不跨线程传递。如果以后引入 {@code @Async}
 * 或自己的线程池，子线程里读不到当前用户（需要显式传递 {@code Authentication}
 * 或设置 {@code SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)}）。
 * 本项目当前没有异步场景，故不处理，但要知道这个边界。
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
     * 取当前登录用户的学号，未登录直接抛异常。
     * <p>
     * 所有需要登录的接口都应该走这个方法：它把"没登录"这件事变成一个明确的错误，
     * 而不是静默返回 null 让调用方自己判断（那正是越权 bug 的温床）。
     *
     * @return 学号
     * @throws IllegalStateException 当前请求没有认证信息
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
     * 是否拥有某个角色（用于少量需要显式判断角色的场景）。
     * <p>
     * 注意：<b>能用 {@code @PreAuthorize} 表达的地方不要用这个方法。</b>
     * 声明式授权写在方法注解上，审计时一眼可见；散在方法体里的 if 判断容易漏。
     * 这里保留它，只为那些"要看数据库里的实体归属"的复杂规则做辅助。
     *
     * @param roleCode 角色编码（1 学生 / 2 管理员 / 3 老师）
     * @return true 表示当前用户是该角色
     */
    public boolean hasRole(int roleCode) {
        return roleCode().filter(code -> code == roleCode).isPresent();
    }

    /**
     * 是否管理员或老师。
     * <p>
     * 这两类角色在多个模块都要用到（学分记录的可见范围、他人报名历史/学分总览的可见范围……），
     * 所以收在这里，而不是让每个 Service 各写一份私有判断：
     * 那种重复一旦有一处漏掉 {@code TEACHER}（或把编码写错），表面上"做了权限判断"，
     * 实际却悄悄放宽或收紧了可见范围，而且很难在代码评审里发现。判断依据只留一处。
     * <p>
     * 用途边界：它回答的是"<b>能看多大范围</b>"，不等于"能不能执行某个具体操作" ——
     * 后者往往还要看数据库里的实体归属（例如"是不是这个活动的发布者"），
     * 那种判断仍应留在对应 Service 里，配合 {@link #hasRole(int)} 使用。
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
