package com.example.zhihuiqingtu.enums;

import lombok.Getter;

/**
 * 活动类型枚举。
 */
@Getter
public enum ActivityTypeEnum {
    SECOND_CLASS(1, "第二课堂学分活动"),
    VOLUNTEER(2, "志愿服务活动"),
    SPORTS(3, "运动健身活动"),
    INTEREST(4, "兴趣团建活动");

    private final Integer code;
    private final String desc;

    ActivityTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
