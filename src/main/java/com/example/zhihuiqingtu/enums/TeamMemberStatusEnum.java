package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 队员状态枚举。
 */
@Getter
public enum TeamMemberStatusEnum {
    PENDING(0, "待审核"),
    JOINED(1, "已加入"),
    REJECTED(2, "已驳回"),
    EXITED(3, "已退出");

    private final Integer code;
    private final String desc;

    TeamMemberStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
