package com.example.zhihuiqingtu.activity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动剩余名额，用于 {@code GET /api/activities/{activityId}/stock}。
 *
 * <p><b>{@code remaining} 由数据库的权威值计算</b>（{@code maxParticipant - currentParticipant}），
 * 不读取 Redis 中的库存计数。Redis 中的值仅用于并发扣减的准入判断，
 * 可能因回补、key 过期重载等原因与真实值存在短暂偏差，不适合作为展示依据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStockResponse {

    /**
     * 活动 ID。
     */
    private Long activityId;

    /**
     * 最大参与人数；null 或 &lt;=0 表示不限人数。
     */
    private Integer maxParticipant;

    /**
     * 当前已报名人数，取自数据库。
     */
    private Integer currentParticipant;

    /**
     * 剩余名额；{@code limited=false} 时为 null。
     * <p>取值时以 {@code Math.max(0, ...)} 兜底，避免异常数据导致负数。
     */
    private Integer remaining;

    /**
     * 是否限制人数；为 false 时前端不应展示剩余名额。
     */
    private Boolean limited;
}
