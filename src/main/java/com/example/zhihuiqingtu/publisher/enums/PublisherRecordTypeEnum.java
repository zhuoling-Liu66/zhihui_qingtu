package com.example.zhihuiqingtu.publisher.enums;

import lombok.Getter;

/**
 * 发布权限记录类型。
 *
 * <p>学生申请与管理员直接调整共用一张记录表，便于在管理后台形成完整的时间线：
 * 谁在什么时候申请、谁审批、谁直接开通或收回。
 */
@Getter
public enum PublisherRecordTypeEnum {
    APPLY(1, "学生申请"),
    GRANT(2, "管理员开通"),
    REVOKE(3, "管理员收回");

    private final Integer code;
    private final String desc;

    PublisherRecordTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按编码反查枚举。
     *
     * @param code 类型编码
     * @return 对应枚举；无匹配时返回 null
     */
    public static PublisherRecordTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PublisherRecordTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
