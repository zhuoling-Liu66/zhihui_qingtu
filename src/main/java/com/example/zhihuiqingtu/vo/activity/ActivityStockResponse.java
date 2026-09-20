package com.example.zhihuiqingtu.vo.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动剩余名额返回对象（{@code GET /api/activities/{activityId}/stock}）。
 *
 * <h3>为什么 remaining 要从数据库算，而不是从 Redis 读</h3>
 * Redis 里那个 {@code activity:stock:{id}} 是为了<b>高并发扣减</b>用的闸门，
 * 它可能因为回补失败、key 过期重载等原因与真实值有<b>短暂偏差</b>；
 * 而展示给用户看的数字应该是权威值 —— 真相永远在 MySQL。
 * 所以这个接口的 {@code remaining} 由 {@code maxParticipant - currentParticipant} 现算。
 * <p>
 * <b>这条区分很重要：Redis 的值用来"做判断"（挡并发，允许极小的误差），
 * 数据库的值用来"给人看"（必须准确）。</b>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStockResponse {

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 最大参与人数；null 或 &lt;=0 表示不限人数。
     */
    private Integer maxParticipant;

    /**
     * 当前已报名人数，来自数据库，是权威值。
     */
    private Integer currentParticipant;

    /**
     * 剩余名额；{@code limited=false} 时为 null。
     * <p>
     * 已做 {@code Math.max(0, max - current)} 保护，防止数据异常时出现负数。
     */
    private Integer remaining;

    /**
     * 是否限制人数。false 时前端不应展示"剩余名额"。
     */
    private Boolean limited;
}
