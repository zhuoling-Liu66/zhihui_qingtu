package com.example.zhihuiqingtu.enrollment.enums;

import lombok.Getter;

/**
 * 发布者/管理员对单条报名记录执行的考勤操作。
 */
@Getter
public enum AttendanceActionEnum {
    MARK_SIGN_IN("MARK_SIGN_IN", "补签到"),
    MARK_SIGN_OUT("MARK_SIGN_OUT", "补签退"),
    REVERT_SIGN_IN("REVERT_SIGN_IN", "撤销签到"),
    REVERT_SIGN_OUT("REVERT_SIGN_OUT", "撤销签退");

    private final String code;
    private final String desc;

    AttendanceActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按编码反查枚举。
     *
     * @param code 操作编码
     * @return 对应枚举；无匹配时返回 null
     */
    public static AttendanceActionEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AttendanceActionEnum value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}
