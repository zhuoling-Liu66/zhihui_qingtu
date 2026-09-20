package com.example.zhihuiqingtu.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.entity.ActivityMain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 活动主表数据访问层。
 * <p>
 * 除了 {@link BaseMapper} 自带的基础 CRUD 外，这里额外提供了两个<b>原子占用/释放名额</b>
 * 的 SQL，它们是报名并发正确性的最终防线（详细推导见 {@code docs/并发问题复现测试文档.md} 第 7、8 节）。
 *
 * <h3>为什么不能继续用 {@code updateById}</h3>
 * 报名的原实现是「先 {@code selectById} 读出人数 → Java 里判断是否已满 →
 * {@code setCurrentParticipant(+1)} → {@code updateById} 写回」，这是典型的
 * <b>check-then-act（先检查后执行）</b>，两个并发请求会同时读到同一个旧值，各自判断都通过，
 * 于是双双报名成功 —— 超卖。
 * <p>
 * 根因是 MySQL 默认隔离级别 REPEATABLE READ 下，普通 {@code SELECT} 是<b>快照读</b>（不加锁，
 * 读事务开始时的照片），所以 {@code @Transactional} 完全挡不住它：事务保证的是"全做或全不做"，
 * 不保证"你读到的值是最新的"。
 * <p>
 * 把「判断」和「+1」压进<b>同一条 UPDATE 的 WHERE 里</b>，就变成了<b>当前读</b>
 * （读最新已提交的数据、且加行锁），原子性由 InnoDB 保证。
 *
 * <h3>为什么 {@code updateById} 还有第二个问题</h3>
 * 它会把 {@link ActivityMain} 的<b>所有字段</b>写回去，于是并发下会把别人刚改的标题、状态等
 * 一起覆盖掉（lost update）。只更新需要变的那一列才是对的，这也是下面两条 SQL 刻意
 * 只 {@code SET current_participant} 的原因。
 */
public interface ActivityMainRepository extends BaseMapper<ActivityMain> {

    /**
     * 原子占用一个名额。
     * <p>
     * 把「判断名额是否已满」和「计数 +1」压进一条 SQL，由数据库保证原子性：
     * <ul>
     *   <li>并发时后到的事务会被行锁挡住，拿到锁后<b>重新读取最新已提交的值</b>再判断 WHERE，
     *       因此不会像快照读那样读到过期数据；</li>
     *   <li>{@code current_participant = current_participant + 1} 是"在库里的当前值上自增"，
     *       而不是"用 Java 里读到的旧值 +1 再写回"，天然免疫丢失更新。</li>
     * </ul>
     *
     * <p>
     * <b>调用顺序很重要：必须在 INSERT {@code activity_enroll} 之前执行。</b>
     * 两个原因：
     * <ol>
     *   <li><b>防超卖</b>：先 UPDATE 才能先拿到行锁并阻塞竞争者，让它们的 WHERE 重新求值；</li>
     *   <li><b>防死锁</b>：{@code activity_enroll} 上有外键指向本表，INSERT 子表时 InnoDB 会在父表
     *       这一行加 <b>S 锁</b>。若先 INSERT，两个事务会各自持有 S 锁、随后都去求 X 锁，
     *       形成循环等待 → 死锁。先 UPDATE 则事务一开始就独占 X 锁，后来者直接在锁上排队，
     *       不存在"S 锁互等"的循环。</li>
     * </ol>
     * 一句话：<b>先拿排他锁，再做外键插入。</b>
     *
     * @param activityId 活动主键
     * @return 影响行数：1 表示成功占到一个名额；0 表示名额已满（或活动不存在/已逻辑删除）
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
     * <p>
     * 同样只更新这一列，并用 {@code GREATEST(..., 0)} 兜住"减到负数"的异常数据。
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
