package com.example.zhihuiqingtu.award.enums;

import lombok.Getter;

/**
 * 学分类型枚举。
 */
@Getter
public enum CreditTypeEnum {
    INNOVATION_ENTREPRENEURSHIP(1, "创新创业"),
    INTELLECTUAL_EDUCATION(2, "智育"),
    MORAL_EDUCATION(3, "德育"),
    PHYSICAL_EDUCATION(4, "体育"),
    AESTHETIC_EDUCATION(5, "美育"),
    LABOR_EDUCATION(6, "劳育");

    private final Integer code;
    private final String desc;

    CreditTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
