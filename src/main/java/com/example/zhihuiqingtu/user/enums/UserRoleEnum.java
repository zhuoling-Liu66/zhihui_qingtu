package com.example.zhihuiqingtu.user.enums;

import lombok.Getter;
import com.example.zhihuiqingtu.security.RoleClaimAuthoritiesConverter;

/**
 * 用户基础角色枚举。
 */
@Getter
public enum UserRoleEnum {
    STUDENT(1, "学生"),
    ADMIN(2, "管理员"),
    TEACHER(3, "老师"),
    /**
     * 管理端普通账号：不是管理员，只能登录管理端发布学分活动并提交审核。
     *
     * <p>与学生/老师账号隔离：客户端登录会拒绝该角色，避免它同时具备前台报名等功能。
     */
    STAFF(4, "运营人员");

    private final Integer code;
    private final String desc;

    UserRoleEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按角色编码反查枚举。
     *
     * <p>供 JWT 角色 claim 映射为权限时使用（见 {@code security/RoleClaimAuthoritiesConverter}）。
     * 与其他枚举的"编码 -> 枚举"查询保持同一命名约定。
     *
     * @param code 角色编码（1 学生 / 2 管理员 / 3 老师 / 4 运营人员）
     * @return 对应枚举；code 为 null 或无匹配时返回 null
     */
    public static UserRoleEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserRoleEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
