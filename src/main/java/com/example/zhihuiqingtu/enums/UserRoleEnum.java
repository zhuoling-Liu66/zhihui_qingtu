package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 用户基础角色枚举。
 */
@Getter
public enum UserRoleEnum {
    STUDENT(1, "学生"),
    ADMIN(2, "管理员"),
    TEACHER(3, "老师");

    private final Integer code;
    private final String desc;

    UserRoleEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
