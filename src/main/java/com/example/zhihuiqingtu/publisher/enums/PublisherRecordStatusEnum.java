package com.example.zhihuiqingtu.publisher.enums;

import lombok.Getter;

/**
 * 发布权限记录状态。
 *
 * <p>只有学生申请会出现 {@link #PENDING}；管理员直接开通/收回会直接落为
 * {@link #APPROVED}。{@link #CLOSED} 用于“管理员已直接调整权限，原待审批申请自动关闭”，
 * 这样不会把用户没被驳回的申请显示成“已驳回”。
 */
@Getter
public enum PublisherRecordStatusEnum {
    PENDING(0, "待审批"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回"),
    CLOSED(3, "已关闭");

    private final Integer code;
    private final String desc;

    PublisherRecordStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按编码反查枚举。
     *
     * @param code 状态编码
     * @return 对应枚举；无匹配时返回 null
     */
    public static PublisherRecordStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PublisherRecordStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
