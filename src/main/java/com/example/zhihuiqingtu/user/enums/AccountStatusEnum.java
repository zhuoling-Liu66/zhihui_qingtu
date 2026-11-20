package com.example.zhihuiqingtu.user.enums;

import lombok.Getter;

/**
 * 账号状态枚举。
 *
 * <p>注意与 {@link PublisherStatusEnum}（发布资格）区分：
 * 本枚举是账号级封禁开关，禁用后登录被拒、已签发令牌立即失效；
 * 发布资格只影响能否发布活动，不影响登录。
 */
@Getter
public enum AccountStatusEnum {
    NORMAL(0, "正常"),
    DISABLED(1, "已禁用");

    private final Integer code;
    private final String desc;

    AccountStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
