package com.example.zhihuiqingtu.service.impl;

import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 活动名额库存服务 —— Redis 在报名链路上的<b>前置闸门</b>。
 *
 * <h2>它解决什么问题</h2>
 * {@code EnrollmentServiceImpl} 里的名额占用已经由
 * {@link ActivityMainRepository#tryOccupySeat(Long)} 这条<b>条件 UPDATE</b> 保证正确性了，
 * 也就是说<b>不用 Redis 也不会超卖</b>。Redis 在这里的作用是<b>性能</b>：
 * 秒杀/抢课时成千上万个请求同时打过来，如果全部直接压到 MySQL 上抢同一行的行锁，
 * 数据库连接池会被瞬间打满、行锁排队导致响应时间飙升。
 * 用 Redis 先把绝大多数"名额早没了"的请求挡在数据库外面，只放行少量真正要竞争的请求。
 *
 * <h2>为什么 Redis 只能当闸门，不能当真相当真相</h2>
 * Redis 可能重启丢数据、可能被误 {@code FLUSHALL}、可能与数据库不一致。
 * 名额这种不能出错的数据，<b>真相永远在 MySQL</b>（{@code current_participant} 那一列）。
 * 所以这里的流程是「Redis 预扣 → DB 条件 UPDATE 定锤 → DB 失败则回补 Redis」，
 * 而不是「Redis 扣成功就算报名成功」。
 *
 * <h2>为什么必须用 Lua 脚本</h2>
 * 「GET 看一眼够不够 → DECRBY 扣掉」是两条命令，中间同样有并发窗口 ——
 * 那只是把 Java 里的 check-then-act 原封不动搬到了 Redis 里。
 * Redis 执行 Lua 脚本期间是<b>单线程原子</b>的，不会有别的命令插进来，
 * 这是 Redis 提供的原子性保证（也是它最常用的原子性手段）。
 *
 * <h2>为什么这里注入的是 StringRedisTemplate 而不是项目里的 RedisTemplate</h2>
 * {@code config/RedisConfig} 里的 {@code redisTemplate} 值序列化器是 <b>JSON</b>。
 * 若用它 {@code execute(script, keys, args)} 传参，{@code args} 会被 JSON 序列化 ——
 * 传字符串 {@code "1"} 进去，脚本里 {@code tonumber(ARGV[1])} 拿到的是带引号的 {@code "\"1\""}，
 * 直接算错或报错。而 {@code StringRedisTemplate} 是 Spring Boot 自动配置好的（无需自己写 @Bean），
 * 用纯字符串序列化，参数原样传递。<b>这也说明"不同用途该用不同模板"不是一个多余的设计。</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityStockService {

    /** 与 Lua 脚本返回码一一对应：扣减成功。 */
    private static final long RESULT_OK = 1L;
    /** 与 Lua 脚本返回码一一对应：库存不足。 */
    private static final long RESULT_SOLD_OUT = 0L;
    /** 与 Lua 脚本返回码一一对应：key 不存在（需要回源加载）。 */
    private static final long RESULT_NOT_INITIALIZED = -1L;

    /**
     * 库存 key 的存活时间。
     * <p>
     * 必须设 TTL：活动结束后不会再有人报名，但 key 如果永久留着就是内存泄漏。
     * 24 小时后自动消失；即使活动还在进行，key 过期也只是退化成"回源加载一次"，
     * 不影响正确性（因为真相在数据库）。
     */
    private static final Duration STOCK_TTL = Duration.ofHours(24);

    /**
     * 原子扣减脚本。
     * <pre>
     * KEYS[1] = activity:stock:{activityId}
     * ARGV[1] = 要扣减的数量（本项目固定为 1）
     * 返回  1 = 扣减成功
     *       0 = 库存不足
     *      -1 = key 不存在，调用方需要先回源加载
     * </pre>
     * 脚本里必须 {@code tonumber()} 转换：Redis 的 GET 返回的是字符串，
     * 直接和数字比较在 Lua 里是「字符串 vs 数字」比较，结果不符合预期。
     */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if stock == false then
                return -1
            end
            if tonumber(stock) < tonumber(ARGV[1]) then
                return 0
            end
            redis.call('DECRBY', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ActivityMainRepository activityMainRepository;

    /**
     * 库存 key 命名遵循项目约定 {@code 业务域:对象:标识}。
     */
    private static String stockKey(Long activityId) {
        return "activity:stock:" + activityId;
    }

    /**
     * 判断该活动是否限制人数。
     * <p>
     * {@code maxParticipant} 为 {@code null} 或 {@code <= 0} 表示<b>不限人数</b>
     * （与 {@code EnrollmentServiceImpl} 里的判断保持一致），这种情况完全跳过 Redis，
     * 直接走数据库路径 —— 没有上限就无所谓"抢名额"。
     *
     * @param activity 活动实体
     * @return true 表示限制人数，需要库存闸门
     */
    public boolean isLimited(ActivityMain activity) {
        return activity != null
                && activity.getMaxParticipant() != null
                && activity.getMaxParticipant() > 0;
    }

    /**
     * 尝试占用一个名额（Redis 层预扣）。
     * <p>
     * key 不存在时会回源数据库加载一次再重试 —— 这是「缓存未命中」的常规处理，
     * 也顺带解决了"活动是修复上线之前建的、Redis 里根本没有这个 key"的历史数据问题。
     *
     * @param activityId 活动主键
     * @return true 表示 Redis 层放行（<b>仍须由数据库层最终确认</b>）；false 表示 Redis 层判定已满
     */
    public boolean tryDeduct(Long activityId) {
        String key = stockKey(activityId);
        Long result = stringRedisTemplate.execute(DEDUCT_SCRIPT, List.of(key), "1");

        if (result != null && result == RESULT_NOT_INITIALIZED) {
            // 缓存未命中：从数据库回源加载，然后重试一次。
            loadFromDb(activityId);
            result = stringRedisTemplate.execute(DEDUCT_SCRIPT, List.of(key), "1");
        }
        return result != null && result == RESULT_OK;
    }

    /**
     * 回补一个名额。
     * <p>
     * <b>这是整个方案里最容易漏掉的一步。</b>数据库失败时 {@code @Transactional} 会自动回滚，
     * 但 Redis <b>不是事务资源</b>，Spring 的事务管理器管不到它 —— 不回补的话这个名额就被"白扣"了，
     * 表现为活动明明还有空位却报"人数已满"。缓存与数据库一致性问题大多源于这类遗漏。
     *
     * @param activityId 活动主键
     */
    public void rollback(Long activityId) {
        stringRedisTemplate.opsForValue().increment(stockKey(activityId));
    }

    /**
     * 重置库存（活动发布、修改最大人数、取消报名后调用）。
     * <p>
     * 直接用数据库的权威值重算 {@code remaining = maxParticipant - currentParticipant}，
     * 而不是在旧值上加减 —— 覆盖式写入能自我纠正历史偏差。
     *
     * @param activityId          活动主键
     * @param maxParticipant      最大人数，null 或 &lt;=0 表示不限
     * @param currentParticipant  当前已报名人数
     */
    public void reset(Long activityId, Integer maxParticipant, Integer currentParticipant) {
        if (maxParticipant == null || maxParticipant <= 0) {
            // 不限人数：删掉库存 key，tryDeduct 的回源分支会直接返回，不会误扣。
            stringRedisTemplate.delete(stockKey(activityId));
            return;
        }
        int current = currentParticipant == null ? 0 : currentParticipant;
        int remaining = Math.max(maxParticipant - current, 0);
        stringRedisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(remaining), STOCK_TTL);
    }

    /**
     * 删除库存 key（活动被删除时调用，避免留下无主 key）。
     *
     * @param activityId 活动主键
     */
    public void evict(Long activityId) {
        stringRedisTemplate.delete(stockKey(activityId));
    }

    /**
     * 从数据库回源加载库存。
     * <p>
     * 用 {@code setIfAbsent} 而不是 {@code set}：并发回源时只有一个请求真正写入，
     * 其余请求复用同一个结果，避免"十个请求各写各的、把刚扣掉的值又覆盖回去"。
     */
    private void loadFromDb(Long activityId) {
        ActivityMain activity = activityMainRepository.selectById(activityId);
        if (activity == null || !isLimited(activity)) {
            return;
        }
        int current = activity.getCurrentParticipant() == null ? 0 : activity.getCurrentParticipant();
        int remaining = Math.max(activity.getMaxParticipant() - current, 0);
        stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey(activityId), String.valueOf(remaining), STOCK_TTL);
        log.debug("回源加载活动库存 activityId={} remaining={}", activityId, remaining);
    }
}
