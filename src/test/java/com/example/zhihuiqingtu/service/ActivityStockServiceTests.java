package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.service.impl.ActivityStockService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ActivityStockService#isLimited} 的行为测试。
 * <p>
 * 这个方法决定"要不要走 Redis 库存闸门"，是整个闸门逻辑的开关。
 * 判断错方向的后果是实打实的：
 * <ul>
 *   <li>该走却没走 → 不限人数的活动被拿去扣库存，报"人数已满"，功能直接坏掉；</li>
 *   <li>不该走却走了 → 限人数的活动绕过了闸门，Redis 白配了（正确性仍在，因为真相在数据库）。</li>
 * </ul>
 * <p>
 * 它同时也是一个纯逻辑方法，不需要 Redis、不需要数据库就能测 ——
 * 所以这里用 {@code mock(...)} 造两个空壳依赖即可，
 * <b>不启动 Spring 容器</b>（一旦启动完整上下文就会去查 MySQL，在没装数据库的机器上必然失败，
 * 这一点和 {@code RedisSerializerTests} 里说明的原因相同）。
 * <p>
 * <b>关于 {@code maxParticipant} 的约定</b>：数据库里 {@code max_participant} 是
 * {@code INT UNSIGNED NOT NULL DEFAULT 0}，<b>0 表示不限人数</b>而不是"限 0 人"。
 * 这个"用 0 表示无穷大"的约定在业务里很常见，也最容易在边界上写错，
 * 所以下面把 null / 0 / 负数 / 正数四种情况都覆盖到。
 */
class ActivityStockServiceTests {

    /** 两个依赖在本测试里都不会被真正调用，用 mock 占位即可。 */
    private final ActivityStockService service = new ActivityStockService(
            mock(StringRedisTemplate.class),
            mock(ActivityMainRepository.class));

    private static ActivityMain activityWithMaxParticipant(Integer maxParticipant) {
        return ActivityMain.builder()
                .activityId(1001L)
                .maxParticipant(maxParticipant)
                .currentParticipant(0)
                .build();
    }

    @Test
    void shouldTreatNullMaxParticipantAsUnlimited() {
        assertFalse(service.isLimited(activityWithMaxParticipant(null)),
                "maxParticipant 为 null 表示不限人数，不应启用库存闸门");
    }

    @Test
    void shouldTreatZeroMaxParticipantAsUnlimited() {
        assertFalse(service.isLimited(activityWithMaxParticipant(0)),
                "maxParticipant = 0 表示不限人数（不是限 0 人），不应启用库存闸门");
    }

    @Test
    void shouldTreatNegativeMaxParticipantAsUnlimited() {
        assertFalse(service.isLimited(activityWithMaxParticipant(-1)),
                "负数属于异常数据，按不限人数处理比当成'限 -1 人'安全");
    }

    @Test
    void shouldTreatPositiveMaxParticipantAsLimited() {
        assertTrue(service.isLimited(activityWithMaxParticipant(1)),
                "maxParticipant = 1 是合法的最小上限");
        assertTrue(service.isLimited(activityWithMaxParticipant(50)),
                "maxParticipant = 50 应启用库存闸门");
    }

    @Test
    void shouldTreatNullActivityAsUnlimited() {
        // 防御性：调用方传入 null 时不应该抛 NPE，而应安全地走"不启用闸门"这一侧
        assertFalse(service.isLimited(null), "活动为 null 时不应启用闸门（更不应抛 NPE）");
    }
}
