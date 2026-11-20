package com.example.zhihuiqingtu.activity.enums;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 活动状态枚举。
 */
@Getter
public enum ActivityStatusEnum {
    PUBLISHED(1, "已发布"),
    FINISHED(3, "已结束"),
    CANCELED(4, "已取消");

    private final Integer code;
    private final String desc;

    ActivityStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 判断是否为允许持久化保存的状态。
     */
    public static boolean isPersistentStatus(Integer code) {
        return PUBLISHED.code.equals(code) || CANCELED.code.equals(code);
    }

    /**
     * 根据结束时间推导前端展示状态。
     */
    public static Integer resolveDisplayStatus(Integer activityStatus, LocalDateTime endTime) {
        if (CANCELED.code.equals(activityStatus)) {
            return CANCELED.code;
        }
        if (endTime != null && !endTime.isAfter(LocalDateTime.now())) {
            return FINISHED.code;
        }
        return PUBLISHED.code;
    }
}
