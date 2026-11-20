package com.example.zhihuiqingtu.activity.support;

import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import com.example.zhihuiqingtu.config.RedisConfig;

/**
 * 活动详情缓存。
 *
 * <p>采用 Cache-Aside：读时先查缓存，未命中回源并写回；写时在数据库更新后<b>删除</b>缓存
 * （而非更新）——并发写会因顺序错乱留下旧值，且未被读取时更新缓存是无效功。
 *
 * <p>缓存对象为 {@link ActivityResponse}，其字段 {@code displayStatus} 由 {@code endTime}
 * 与当前时间计算得出，<b>读取后必须重新计算</b>，不能直接返回缓存中的值，
 * 否则活动结束后一段时间内仍会展示为进行中。详见 {@link #get}。
 *
 * <p>防穿透：查询不存在的活动时写入短 TTL 空值标记（{@link #putMissing}）。<br>
 * 防雪崩：写入缓存时给 TTL 附加随机抖动，避免大量 key 同时过期。
 *
 * <p><b>失效范围</b>：任何改变活动详情字段的写路径都必须调用 {@link #evict}，
 * 包括报名/取消报名（它们会改变 {@code currentParticipant}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheService {

    /** 详情缓存存活 10 分钟。写路径会主动删除，所以这个 TTL 只是兜底。 */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);

    /** 空值标记的存活时间。短一些：万一活动随后被创建出来，也不至于一直 404。 */
    private static final Duration NULL_TTL = Duration.ofMinutes(1);

    /**
     * 空值标记的内容，写的什么不重要（只用 {@code hasKey} 判断存在性）。
     */
    private static final String NULL_MARKER = "1";

    /**
     * 用 JSON 序列化的 RedisTemplate（来自 {@code config/RedisConfig}）。
     * <p>
     * 这里能直接存整个 {@code ActivityResponse} 对象并原样取回，前提是那个配置开了
     * default typing（JSON 里带 {@code @class}），否则取回来会退化成 {@code LinkedHashMap}、
     * 强转时抛 {@code ClassCastException}。{@code RedisSerializerTests} 已经验证过这条路径。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    private static String detailKey(Long activityId) {
        return "activity:detail:" + activityId;
    }

    private static String nullKey(Long activityId) {
        return "activity:detail:null:" + activityId;
    }

    /**
     * 读缓存。
     *
     * <p><b>调用方注意</b>：返回对象中的 {@code displayStatus} 是写入时计算并随 JSON 一起
     * 持久化的值，读取时可能已经过期（该字段依赖当前时间），必须重新计算后再返回给前端。
     *
     * @param activityId 活动主键
     * @return 缓存的详情；null 表示未命中
     */
    public ActivityResponse get(Long activityId) {
        Object cached = redisTemplate.opsForValue().get(detailKey(activityId));
        return cached instanceof ActivityResponse response ? response : null;
    }

    /**
     * 是否命中"该活动不存在"的空值标记。
     *
     * @param activityId 活动主键
     * @return true 表示短期内已经确认过这个活动不存在
     */
    public boolean isKnownMissing(Long activityId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(nullKey(activityId)));
    }

    /**
     * 写入详情缓存，TTL 加随机抖动防雪崩。
     *
     * @param response 活动详情
     */
    public void put(ActivityResponse response) {
        if (response == null || response.getActivityId() == null) {
            return;
        }
        long jitter = ThreadLocalRandom.current().nextLong(60);
        redisTemplate.opsForValue().set(
                detailKey(response.getActivityId()),
                response,
                DETAIL_TTL.plusSeconds(jitter));
    }

    /**
     * 缓存"活动不存在"，短 TTL 防穿透。
     *
     * @param activityId 活动主键
     */
    public void putMissing(Long activityId) {
        redisTemplate.opsForValue().set(nullKey(activityId), NULL_MARKER, NULL_TTL);
    }

    /**
     * 失效缓存。任何修改活动详情字段的写路径都必须调用。
     *
     * <p>以删除而非更新的方式失效，原因见类注释。当前需要调用的写路径：
     * {@code updateActivity}、{@code changeActivityStatus}、{@code updateSignInSwitch}、
     * {@code deleteActivity}、{@code createEnrollment}、{@code cancelEnrollment}。
     * 新增写路径时须一并确认，遗漏会导致前端读到过期数据。
     *
     * @param activityId 活动主键
     */
    public void evict(Long activityId) {
        redisTemplate.delete(List.of(detailKey(activityId), nullKey(activityId)));
    }

    /**
     * Cache-Aside 的读模板：查缓存 → 未命中回源 → 写回缓存。
     *
     * <p>把回源方式作为参数传入，使调用方只需提供查询逻辑，避免各处重复实现缓存判断
     * 而漏掉写回或空值标记。
     *
     * @param activityId 活动主键
     * @param loader     回源函数，返回 null 表示数据库中不存在该记录
     * @return 活动详情；数据库中不存在时返回 null，由调用方决定如何报错
     */
    public ActivityResponse getOrLoad(Long activityId, Function<Long, ActivityResponse> loader) {
        ActivityResponse cached = get(activityId);
        if (cached != null) {
            log.debug("活动详情缓存命中 activityId={}", activityId);
            return cached;
        }
        if (isKnownMissing(activityId)) {
            log.debug("活动详情命中空值标记（防穿透） activityId={}", activityId);
            return null;
        }

        ActivityResponse loaded = loader.apply(activityId);
        if (loaded == null) {
            putMissing(activityId);
            return null;
        }
        put(loaded);
        return loaded;
    }
}
