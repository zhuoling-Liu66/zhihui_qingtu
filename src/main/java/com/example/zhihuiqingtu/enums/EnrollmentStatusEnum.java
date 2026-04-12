package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 报名状态枚举。
 */
@Getter
public enum EnrollmentStatusEnum {
    ENROLLED(1, "已报名"),
    SIGNED_IN(3, "已签到"),
    COMPLETED(4, "已完成");

    private final Integer code;
    private final String desc;

    EnrollmentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
