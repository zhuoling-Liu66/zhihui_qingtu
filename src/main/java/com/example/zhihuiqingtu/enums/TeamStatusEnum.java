package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 队伍状态枚举。
 */
@Getter
public enum TeamStatusEnum {
    RECRUITING(0, "招募中"),
    FULL(1, "已满员"),
    DISMISSED(2, "已解散");

    private final Integer code;
    private final String desc;

    TeamStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
