package com.example.zhihuiqingtu.award.enums;

import lombok.Getter;
import com.example.zhihuiqingtu.activity.enums.ReviewStatusEnum;

/**
 * 发放记录状态。
 *
 * <p>与活动审核状态（{@link ReviewStatusEnum}）分开命名，避免两套业务语义混用：
 * 发放记录多了“待管理员审核”这一中间态，用于灵活志愿时长。
 */
@Getter
public enum AwardStatusEnum {
    PENDING(0, "待确认"),
    APPROVED(1, "已发放"),
    REJECTED(2, "不发放"),
    PENDING_REVIEW(3, "待审核");

    private final Integer code;
    private final String desc;

    AwardStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
