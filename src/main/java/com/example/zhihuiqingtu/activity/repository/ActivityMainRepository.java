package com.example.zhihuiqingtu.activity.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
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
               AND review_status = 1
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

    /**
     * 只更新活动可编辑字段，并在同一条 SQL 里校验"最大人数不小于当前报名人数"。
     *
     * <p><b>为什么不用 {@code updateById}</b>：
     * <ol>
     *   <li>它会写回实体全部字段，包括读出来之后就再没碰过的 {@code current_participant}。
     *       编辑活动与并发报名交错时，旧值会把刚增加的报名人数覆盖掉（丢失更新）；</li>
     *   <li>容量校验若只放在 Java 里，校验通过与实际写入之间仍有并发窗口，
     *       校验之后又有人报名，就会把最大人数写到小于实际报名数。</li>
     * </ol>
     * 把容量条件并入 {@code WHERE} 后，校验与写入是同一个原子操作。
     *
     * <p>不更新 {@code activity_status}、{@code sign_in_enabled}、{@code publisher_user_id}：
     * 这些字段各有独立接口，混在一起写回同样会互相覆盖。
     *
     * @param activity 已赋好可编辑字段的活动实体（{@code currentParticipant} 不参与更新）
     * @return 影响行数；0 表示活动不存在/已删除，或最大人数已小于当前报名人数
     */
    @Update("""
            UPDATE activity_main
               SET activity_title = #{activity.activityTitle},
                   activity_desc = #{activity.activityDesc},
                   activity_type = #{activity.activityType},
                   activity_credit = #{activity.activityCredit},
                   credit_type = #{activity.creditType},
                   credit_mode = #{activity.creditMode},
                   volunteer_hours = #{activity.volunteerHours},
                   volunteer_mode = #{activity.volunteerMode},
                   cover_url = #{activity.coverUrl},
                   start_time = #{activity.startTime},
                   end_time = #{activity.endTime},
                   activity_location = #{activity.activityLocation},
                   max_participant = #{activity.maxParticipant},
                   review_status = 0,
                   review_reason = NULL,
                   reviewer_user_id = NULL,
                   review_time = NULL,
                   update_time = NOW()
             WHERE activity_id = #{activity.activityId}
               AND delete_flag = 0
               AND (COALESCE(#{activity.maxParticipant}, 0) <= 0
                    OR current_participant <= #{activity.maxParticipant})
            """)
    int updateEditableFields(@Param("activity") ActivityMain activity);

    /**
     * 只更新活动状态这一列。
     *
     * @param activityId     活动主键
     * @param activityStatus 目标状态（1-已发布 / 4-已取消）
     * @return 影响行数；0 表示活动不存在或已被删除
     */
    @Update("""
            UPDATE activity_main
               SET activity_status = #{activityStatus},
                   update_time = NOW()
             WHERE activity_id = #{activityId}
               AND delete_flag = 0
            """)
    int updateStatus(@Param("activityId") Long activityId,
                     @Param("activityStatus") Integer activityStatus);

    /**
     * 只更新签到开关这一列。
     *
     * @param activityId    活动主键
     * @param signInEnabled 0-关闭 / 1-开启
     * @return 影响行数；0 表示活动不存在或已被删除
     */
    @Update("""
            UPDATE activity_main
               SET sign_in_enabled = #{signInEnabled},
                   update_time = NOW()
             WHERE activity_id = #{activityId}
               AND delete_flag = 0
            """)
    int updateSignInEnabled(@Param("activityId") Long activityId,
                            @Param("signInEnabled") Integer signInEnabled);

    /**
     * 写入审核结论。
     *
     * <p>只允许处理仍在待审核或已被驳回的活动；已通过的活动再次审核会返回 0，
     * 由服务层转换为“状态已变化，请刷新”的冲突提示，避免两个管理员同时操作时后写覆盖先写。
     *
     * @param activityId      活动主键
     * @param reviewStatus    目标审核状态（1 已通过 / 2 已驳回）
     * @param reviewReason    驳回原因或备注
     * @param reviewerUserId  审核人学号
     * @param reviewTime      审核时间
     * @return 影响行数；0 表示活动不存在或已经审核通过
     */
    @Update("""
            UPDATE activity_main
               SET review_status = #{reviewStatus},
                   review_reason = #{reviewReason},
                   reviewer_user_id = #{reviewerUserId},
                   review_time = #{reviewTime},
                   update_time = NOW()
             WHERE activity_id = #{activityId}
               AND delete_flag = 0
               AND review_status IN (0, 2)
            """)
    int reviewActivity(@Param("activityId") Long activityId,
                       @Param("reviewStatus") Integer reviewStatus,
                       @Param("reviewReason") String reviewReason,
                       @Param("reviewerUserId") String reviewerUserId,
                       @Param("reviewTime") java.time.LocalDateTime reviewTime);
}
