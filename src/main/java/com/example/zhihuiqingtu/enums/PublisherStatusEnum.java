package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 活动发布资格状态枚举。
 */
@Getter
public enum PublisherStatusEnum {
    DISABLED(0, "未开通"),
    ENABLED(1, "已开通");

    private final Integer code;
    private final String desc;

    PublisherStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
