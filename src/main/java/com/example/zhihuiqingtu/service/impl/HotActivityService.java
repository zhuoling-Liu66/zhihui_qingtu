package com.example.zhihuiqingtu.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 热门活动榜服务 —— Redis {@code ZSet}（有序集合）的应用。
 *
 * <h2>为什么用 ZSet 而不是 MySQL</h2>
 * ZSet 是 Redis 独有、关系型数据库很难优雅实现的结构：<b>每个成员带一个分数，且按分数自动排序</b>。
 * 排行榜是它的教科书场景。
 * <p>
 * 用 MySQL 做「报名人数 Top 10」需要 {@code ORDER BY count DESC LIMIT 10} 并扫描全表；
 * ZSet 的 {@code ZREVRANGE key 0 9} 是 {@code O(log N + 10)}，且天然实时。
 *
 * <h2>为什么只存 activityId 而不存整个活动对象</h2>
 * 排行榜的分数会<b>频繁变动</b>（每次报名都要 +1）。如果把整个 {@code ActivityResponse}
 * 塞进 ZSet 成员里，每次报名都要重写一遍大对象，既浪费带宽又容易不一致。
 * 存 ID + 回表补详情，是空间、性能与一致性的折中。
 *
 * <h2>为什么这里也用 StringRedisTemplate</h2>
 * ZSet 的 member 必须是字符串，score 必须是数字。项目里 JSON 序列化版的 {@code RedisTemplate}
 * 会把 member 也做 JSON 编码（写入 {@code 1001} 时存进去的可能是带引号的形式），
 * 用 {@code redis-cli zrevrange} 查看时不好读，也不方便和其他工具互通。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotActivityService {

    /**
     * 榜单 key。周榜：7 天自动过期，避免历史上的爆款活动永远霸榜。
     */
    private static final String HOT_RANK_KEY = "activity:hot:enroll";

    /** 榜单存活时间。 */
    private static final Duration RANK_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 报名成功后给该活动加分。
     * <p>
     * {@code incrementScore} 对应 Redis 的 {@code ZINCRBY}：它内部就是「读 + 加 + 写」，
     * 但由 Redis 单线程保证不会被打断，<b>本身就是原子的</b> —— 所以这里不需要写 Lua 脚本。
     * 对比一下 §6 的登录失败计数（那里必须 Lua 或 SET NX + INCR 组合），
     * 可以体会到"Redis 哪些命令已经是原子的、哪些需要自己组合"是使用它的关键判断。
     * <p>
     * {@code incrementScore} 还有一个便利特性：<b>成员不存在时会自动创建并把分数设为增量</b>，
     * 所以不需要先判断 key/成员是否存在。
     *
     * @param activityId 活动主键
     */
    public void incrementEnroll(Long activityId) {
        stringRedisTemplate.opsForZSet().incrementScore(HOT_RANK_KEY, String.valueOf(activityId), 1);
        // 顺便续期：榜单只要还在被使用就不该中途过期。
        // 用 expire 的返回值做"只在 key 没有 TTL 时才补设"的廉价优化，
        // 避免每次报名都多发一条 EXPIRE 命令。
        if (Boolean.FALSE.equals(stringRedisTemplate.expire(HOT_RANK_KEY, RANK_TTL))) {
            log.debug("热门榜续期未生效（key 可能刚过期）: {}", HOT_RANK_KEY);
        }
    }

    /**
     * 取消报名后减分。
     * <p>
     * 分数可能被减到 0 甚至负数（例如历史数据缺失时取消了一条没统计过的报名）。
     * 对排行榜来说负分没有意义，所以减到 {@code <= 0} 时直接把成员从榜上移除（{@code ZREM}），
     * 避免出现"报名人数为负"的荒唐条目。
     *
     * @param activityId 活动主键
     */
    public void decrementEnroll(Long activityId) {
        String member = String.valueOf(activityId);
        Double score = stringRedisTemplate.opsForZSet().incrementScore(HOT_RANK_KEY, member, -1);
        if (score != null && score <= 0) {
            stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, member);
        }
    }

    /**
     * 活动被删除时从榜上摘掉，避免榜单里残留已经被删除的 ID。
     *
     * @param activityId 活动主键
     */
    public void remove(Long activityId) {
        stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, String.valueOf(activityId));
    }

    /**
     * 取报名人数最高的前 N 个活动 ID（降序）。
     * <p>
     * 返回集合是 {@link LinkedHashSet} —— <b>顺序即排名，调用方不能再用别的顺序重排</b>。
     *
     * @param limit 取前多少条
     * @return 活动 ID 列表（已按分数降序）
     */
    public List<Long> listHotActivityIds(int limit) {
        Set<String> members = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_RANK_KEY, 0, limit - 1L);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String member : members) {
            try {
                ids.add(Long.valueOf(member));
            } catch (NumberFormatException exception) {
                // 榜单里混进了非数字成员（人为往 Redis 里塞脏数据）：跳过即可，不能让整个接口失败
                log.warn("热门榜里存在非法成员，已跳过：{}", member);
            }
        }
        return List.copyOf(ids);
    }

    /**
     * 查询某个活动的当前分数（累计报名人数，来自 Redis 的近似值）。
     *
     * @param activityId 活动主键
     * @return 分数；榜单里没有该成员时返回 null
     */
    public Double getScore(Long activityId) {
        return stringRedisTemplate.opsForZSet().score(HOT_RANK_KEY, String.valueOf(activityId));
    }

    /**
     * 带分数读取榜单，仅用于调试/管理视角。
     *
     * @param limit 取前多少条
     * @return 成员 → 分数的有序集合视图
     */
    public Set<ZSetOperations.TypedTuple<String>> listHotWithScores(int limit) {
        return stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_RANK_KEY, 0, limit - 1L);
    }
}
