package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 审核状态枚举。
 */
@Getter
public enum ReviewStatusEnum {
    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回");

    private final Integer code;
    private final String desc;

    ReviewStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
