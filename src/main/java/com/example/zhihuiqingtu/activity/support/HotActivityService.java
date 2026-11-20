package com.example.zhihuiqingtu.activity.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 热门活动榜，基于 Redis ZSet（有序集合）。
 *
 * <p>ZSet 中仅存 {@code activityId} 作为成员、累计报名人数作为分数。不存完整活动对象，
 * 是因为分数变动频繁（每次报名都要更新），存大对象会显著增加写入开销；
 * 读取时再按 id 批量回表补全详情。
 *
 * <p>使用 {@link StringRedisTemplate}：ZSet 的成员与分数均为字符串/数值，
 * 用 JSON 序列化模板会使成员被 JSON 编码，影响 {@code redis-cli} 等工具的可读性与互通性。
 *
 * <p><b>维护约束</b>：报名成功、取消报名、活动删除三处必须分别调用
 * {@link #incrementEnroll}、{@link #decrementEnroll}、{@link #remove}，
 * 否则榜单会与实际报名数偏离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotActivityService {

    /** 榜单 key；分数为累计报名人数，7 天过期以形成周榜。 */
    private static final String HOT_RANK_KEY = "activity:hot:enroll";

    /** 榜单存活时间。 */
    private static final Duration RANK_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 报名成功后给该活动加分。
     * <p>
     * {@code incrementScore} 对应 {@code ZINCRBY}，本身即为原子操作，无需 Lua 脚本。
     * 成员不存在时会自动创建，无需预先判断。
     *
     * @param activityId 活动主键
     */
    public void incrementEnroll(Long activityId) {
        stringRedisTemplate.opsForZSet().incrementScore(HOT_RANK_KEY, String.valueOf(activityId), 1);
        // 续期：榜单只要仍在被使用就不应中途过期
        if (Boolean.FALSE.equals(stringRedisTemplate.expire(HOT_RANK_KEY, RANK_TTL))) {
            log.debug("热门榜续期未生效（key 可能刚过期）: {}", HOT_RANK_KEY);
        }
    }

    /**
     * 取消报名后减分。
     *
     * <p>分数减至 {@code <= 0} 时移除该成员，避免榜单出现无意义的零分或负分条目
     * （例如取消了一条未被统计过的报名）。
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
     * 从榜单移除（活动被删除时调用，避免残留已不存在的 id）。
     *
     * @param activityId 活动主键
     */
    public void remove(Long activityId) {
        stringRedisTemplate.opsForZSet().remove(HOT_RANK_KEY, String.valueOf(activityId));
    }

    /**
     * 取报名人数最高的前 N 个活动 id，按分数降序。
     *
     * <p><b>返回值有顺序语义（即排名）</b>，调用方不得重排。
     *
     * @param limit 取前多少条
     * @return 活动 id 列表，已按分数降序
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
}
