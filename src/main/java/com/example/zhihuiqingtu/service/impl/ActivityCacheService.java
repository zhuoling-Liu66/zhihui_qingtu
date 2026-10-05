package com.example.zhihuiqingtu.service.impl;

import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 活动详情缓存服务 —— Redis 在活动查询链路上的应用。
 *
 * <h2>为什么值得缓存</h2>
 * {@code ActivityServiceImpl.getActivityDetail} 表面只是"查一个活动"，
 * 但构造返回对象时还要<b>再查两张表</b>（{@code activity_tag_rel} 查关联、{@code activity_tag} 查标签名），
 * 也就是一次详情请求至少 3 条 SQL。而它被 5 个地方调用 ——
 * 包括"改活动状态""开关签到"这些<b>本来只影响 1 行</b>的写操作，写完都要重新查 3 张表把详情返回给前端。
 * 活动详情是典型的「读多写少」，缓存收益明显。
 *
 * <h2>缓存模式：Cache-Aside（旁路缓存）</h2>
 * <pre>
 * 读：先查 Redis → 命中直接返回
 *                → 未命中查 DB → 写回 Redis → 返回
 * 写：先更新 DB → 再【删除】Redis（DEL，而不是更新缓存）
 * </pre>
 * <b>为什么写的时候删除而不是更新缓存？</b>两个原因：
 * <ol>
 *   <li>并发写会写脏：A、B 同时更新，A 先更新 DB 再更新缓存、B 后更新 DB 后更新缓存，
 *       顺序错乱时缓存里会留下 A 的旧值；</li>
 *   <li>惰性加载更划算：如果这个活动写完之后根本没人读，更新缓存就是白做功。</li>
 * </ol>
 *
 * <h2>三个经典缓存问题的应对</h2>
 * <table border="1">
 *   <tr><th>问题</th><th>是什么</th><th>本类的应对</th></tr>
 *   <tr>
 *     <td>缓存穿透</td>
 *     <td>反复请求不存在的 ID，每次都穿透到数据库</td>
 *     <td>{@link #putMissing} 写一个 60 秒的空值标记</td>
 *   </tr>
 *   <tr>
 *     <td>缓存雪崩</td>
 *     <td>大量 key 同一秒过期，请求同时压向数据库</td>
 *     <td>{@link #put} 给 TTL 加 0~60 秒随机抖动，把过期时间打散</td>
 *   </tr>
 *   <tr>
 *     <td>缓存击穿</td>
 *     <td>某个热点 key 刚好过期，瞬间大量请求打到它</td>
 *     <td>本项目单机教学场景暂未处理（工程上可用互斥锁/逻辑过期），见方案文档说明</td>
 *   </tr>
 * </table>
 *
 * <h2>本场景特有的坑：不要把 displayStatus 缓存进去</h2>
 * 数据库里 {@code activity_status} 只存 1（已发布）和 4（已取消），
 * 前端看到的 3（已结束）是 {@code ActivityStatusEnum.resolveDisplayStatus} 用
 * {@code endTime} 和 {@code LocalDateTime.now()} <b>现算</b>的。
 * 如果把算好的 {@code displayStatus} 缓存 10 分钟，一个 10:00 结束的活动在 10:00~10:10
 * 会一直显示"进行中"，而且数据库是对的、代码是对的，<b>只有缓存是错的</b>，极难排查。
 * <p>
 * 处理办法：缓存里存「事实」（{@code activityStatus} + {@code endTime}），
 * 返回前重新计算「结论」（{@code displayStatus}）。代码位置见
 * {@code ActivityServiceImpl#toActivityResponse}。
 * <p>
 * <b>这条原则可以推广：缓存"事实"，不要缓存"结论"。凡是依赖 now() 的派生值都不要进缓存。</b>
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
     * 让缓存失效。活动发生任何变更后都必须调用。
     * <p>
     * 注意是<b>删除</b>
     * <p>
     * <b>哪些路径需要调用</b>，是缓存改造中最容易出错的部分，本项目逐一核对的结论是：
     * <pre>
     * updateActivity         ✅ 需要（改了标题/时间/人数等）
     * changeActivityStatus   ✅ 需要（改了 activity_status）
     * updateSignInSwitch     ✅ 需要（改了 sign_in_enabled）
     * deleteActivity         ✅ 需要
     * createEnrollment       ✅ 需要（改了 current_participant，详情里含这个字段）
     * cancelEnrollment       ✅ 需要（同上）
     * </pre>
     *
     * @param activityId 活动主键
     */
    public void evict(Long activityId) {
        redisTemplate.delete(List.of(detailKey(activityId), nullKey(activityId)));
    }

    /**
     * Cache-Aside 的"读"模板方法：把「查缓存 → 回源 → 写回」这套重复流程收在一处。
     * <p>
     * 用 {@link Function} 把"怎么从数据库查"作为参数传进来，调用方只需要提供回源逻辑，
     * 不必在每处都手写一遍缓存判断（也就不会漏掉写回或空值缓存）。
     *
     * @param activityId 活动主键
     * @param loader     回源函数；返回 null 表示数据库里确实没有这条记录
     * @return 活动详情；数据库里不存在时返回 null（调用方负责抛 4100）
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
