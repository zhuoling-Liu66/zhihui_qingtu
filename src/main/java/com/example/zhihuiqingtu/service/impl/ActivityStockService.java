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
 * 活动名额库存的 Redis 前置闸门。
 *
 * <p><b>定位：性能优化，不承担正确性。</b>名额占用的正确性由
 * {@link ActivityMainRepository#tryOccupySeat(Long)} 的条件 UPDATE 保证，
 * 本类只负责把大量注定失败的请求挡在数据库之外，降低行锁竞争与连接池压力。
 * 因此 Redis 数据丢失或不可用只会退化为性能下降，不会造成超卖。
 *
 * <p>流程为「Redis 预扣 → 数据库条件 UPDATE 确认 → 数据库失败则回补 Redis」。
 * 数据库是唯一事实来源，Redis 中的值仅作闸门。
 *
 * <p><b>维护约束</b>
 * <ol>
 *   <li>扣减必须走 Lua 脚本：{@code GET} 与 {@code DECRBY} 分两条命令会重新引入
 *       并发窗口，等同于把检查-执行竞态搬进 Redis；</li>
 *   <li>必须使用 {@link StringRedisTemplate} 而非 JSON 序列化的 {@code RedisTemplate}，
 *       否则脚本参数会被 JSON 编码，{@code tonumber(ARGV[1])} 得到带引号的字符串；</li>
 *   <li>数据库扣减失败或后续步骤抛异常时，<b>必须回补</b>（{@link #rollback}）——
 *       Spring 事务不会回滚 Redis 中的扣减。</li>
 * </ol>
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
     * 库存 key 的存活时间，用于避免活动结束后残留无主 key。
     * <p>
     * 即使 key 提前过期也只会触发一次回源加载，不影响正确性。
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
     * 必须用 {@code tonumber()} 转换：Redis 返回的是字符串，Lua 中字符串与数字直接比较
     * 不会得到预期结果。
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
     *
     * <p>{@code maxParticipant} 为 {@code null} 或 {@code <= 0} 表示不限人数
     * （与 {@code EnrollmentServiceImpl} 的判断保持一致），此时不启用闸门，直接走数据库。
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
     * 尝试预扣一个名额。
     *
     * <p>key 不存在时先回源加载再重试，因此活动未初始化库存也能正常工作。
     *
     * @param activityId 活动主键
     * @return true 表示 Redis 层放行，<b>仍须由数据库条件 UPDATE 最终确认</b>
     */
    public boolean tryDeduct(Long activityId) {
        String key = stockKey(activityId);
        Long result = stringRedisTemplate.execute(DEDUCT_SCRIPT, List.of(key), "1");

        if (result != null && result == RESULT_NOT_INITIALIZED) {
            loadFromDb(activityId);
            result = stringRedisTemplate.execute(DEDUCT_SCRIPT, List.of(key), "1");
        }
        return result != null && result == RESULT_OK;
    }

    /**
     * 回补一个名额。数据库扣减失败或后续步骤抛异常时必须调用。
     *
     * <p><b>不能依赖事务自动回滚</b>：Redis 不是事务资源，{@code @Transactional} 的回滚
     * 只作用于数据库。遗漏回补会导致名额被无谓消耗，表现为活动仍有空位却提示人数已满。
     *
     * @param activityId 活动主键
     */
    public void rollback(Long activityId) {
        stringRedisTemplate.opsForValue().increment(stockKey(activityId));
    }

    /**
     * 按数据库权威值重置库存（活动发布、修改最大人数后调用）。
     *
     * <p>以 {@code maxParticipant - currentParticipant} 覆盖写入，而非在旧值上增减，
     * 这样可自动纠正历史偏差。
     *
     * @param activityId          活动主键
     * @param maxParticipant      最大人数，null 或 &lt;=0 表示不限
     * @param currentParticipant  当前已报名人数
     */
    public void reset(Long activityId, Integer maxParticipant, Integer currentParticipant) {
        if (maxParticipant == null || maxParticipant <= 0) {
            // 不限人数：移除 key，使 tryDeduct 的回源分支不产生扣减
            stringRedisTemplate.delete(stockKey(activityId));
            return;
        }
        int current = currentParticipant == null ? 0 : currentParticipant;
        int remaining = Math.max(maxParticipant - current, 0);
        stringRedisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(remaining), STOCK_TTL);
    }

    /**
     * 删除库存 key（活动被删除时调用，避免残留无主 key）。
     *
     * @param activityId 活动主键
     */
    public void evict(Long activityId) {
        stringRedisTemplate.delete(stockKey(activityId));
    }

    /**
     * 从数据库回源加载库存。
     *
     * <p>用 {@code setIfAbsent} 而非 {@code set}：并发回源时只有第一个写入生效，
     * 避免多个请求各自写入而覆盖掉刚刚完成的扣减。
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
