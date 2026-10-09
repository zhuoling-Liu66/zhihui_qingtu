package com.example.zhihuiqingtu.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.entity.ActivityMain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 活动主表数据访问层。
 *
 * <p>除 {@link BaseMapper} 的基础 CRUD 外，额外提供两个<b>原子占用/释放名额</b>的 SQL，
 * 用于保证报名并发下的正确性。
 *
 * <p><b>为什么不使用 {@code updateById} 维护名额计数</b>：该做法需先查询、再判断、后写回，
 * 属于检查-执行竞态。MySQL 默认隔离级别下普通 {@code SELECT} 为快照读，不阻塞并发事务，
 * 两个事务可能读到同一旧值并同时通过校验，导致超卖；且 {@code updateById} 会写回实体全部字段，
 * 造成丢失更新。将判断条件并入 {@code UPDATE} 的 {@code WHERE} 后，该语句为当前读并持有行锁，
 * 并发事务会重新读取最新已提交值再求值条件，由 InnoDB 保证原子性。
 */
public interface ActivityMainRepository extends BaseMapper<ActivityMain> {

    /**
     * 原子占用一个名额。
     *
     * <p><b>必须在插入报名记录之前调用</b>，原因有两点：
     * <ol>
     *   <li>防超卖：先执行更新可先取得行锁并使竞争者阻塞，其 {@code WHERE} 将基于最新值求值；</li>
     *   <li>防死锁：{@code activity_enroll} 的外键在插入时会于本表对应行加共享锁。
     *       若先插入后更新，两个事务可能各自持有共享锁并互相等待对方释放以取得排他锁，
     *       形成循环等待。先更新则事务一开始即独占排他锁，不存在该循环。</li>
     * </ol>
     *
     * @param activityId 活动主键
     * @return 影响行数；1 表示占用成功，0 表示名额已满或活动不存在、已被逻辑删除
     */
    @Update("""
            UPDATE activity_main
               SET current_participant = current_participant + 1
             WHERE activity_id = #{activityId}
               AND delete_flag = 0
               AND (max_participant IS NULL OR max_participant <= 0
                    OR current_participant < max_participant)
            """)
    int tryOccupySeat(@Param("activityId") Long activityId);

    /**
     * 原子释放一个名额（取消报名时调用）。
     *
     * <p>仅更新计数列，并以 {@code GREATEST} 保证不会减至负数。
     *
     * @param activityId 活动主键
     * @return 影响行数
     */
    @Update("""
            UPDATE activity_main
               SET current_participant = GREATEST(current_participant - 1, 0)
             WHERE activity_id = #{activityId}
               AND delete_flag = 0
            """)
    int releaseSeat(@Param("activityId") Long activityId);
}
