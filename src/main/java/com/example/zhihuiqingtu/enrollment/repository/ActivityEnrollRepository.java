package com.example.zhihuiqingtu.enrollment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.enrollment.entity.ActivityEnroll;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 活动报名数据访问层。
 *
 * <p>取消与签到都使用<b>带当前状态条件的 UPDATE</b>，而不是"先查询判断、再按主键更新"：
 * 后者两个并发请求会同时读到"已报名"，都通过判断，导致名额被释放两次、
 * 或同一个报名生成两条学分记录。把状态条件写进 {@code WHERE} 后，
 * 数据库保证只有一个请求能影响 1 行，另一个影响 0 行。
 */
public interface ActivityEnrollRepository extends BaseMapper<ActivityEnroll> {

    /**
     * 取消报名：仅当记录仍处于"已报名"且未签到时成功。
     *
     * <p>取消沿用逻辑删除（{@code delete_flag = 1}），与唯一索引
     * {@code uk_activity_enroll_user (user_id, activity_id, delete_flag)} 配合：
     * 取消后用户仍可以重新报名，新记录是 {@code delete_flag = 0}，不会与历史记录冲突。
     *
     * @param enrollId 报名主键
     * @param userId   当前登录用户（同时作为归属条件，防止取消别人的报名）
     * @return 影响行数；1 表示本次取消成功，0 表示已被取消或状态已变化
     */
    @Update("""
            UPDATE activity_enroll
               SET delete_flag = 1,
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND user_id = #{userId}
               AND enroll_status = 1
               AND sign_in_time IS NULL
               AND delete_flag = 0
            """)
    int cancelIfEnrolled(@Param("enrollId") Long enrollId,
                         @Param("userId") String userId);

    /**
     * 自助签到：仅当记录仍处于"已报名"时成功，流转到"已签到"并写入签到时间。
     *
     * <p>签到不再直接完成活动：完成与发放记录的生成都推迟到签退，避免“签完到就走”
     * 也能拿到学分/时长。
     *
     * @param enrollId   报名主键
     * @param userId     当前登录用户（归属条件）
     * @param signInTime 签到时间
     * @return 影响行数；1 表示本次签到成功，0 表示已签到或不是已报名状态
     */
    @Update("""
            UPDATE activity_enroll
               SET enroll_status = 3,
                   sign_in_time = #{signInTime},
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND user_id = #{userId}
               AND enroll_status = 1
               AND sign_in_time IS NULL
               AND delete_flag = 0
            """)
    int signInIfEnrolled(@Param("enrollId") Long enrollId,
                         @Param("userId") String userId,
                         @Param("signInTime") LocalDateTime signInTime);

    /**
     * 自助签退：仅当记录处于"已签到"时成功，流转到"已完成"并写入签退时间。
     *
     * @param enrollId    报名主键
     * @param userId      当前登录用户（归属条件）
     * @param signOutTime 签退时间
     * @return 影响行数；1 表示本次签退成功，0 表示未签到或已签退
     */
    @Update("""
            UPDATE activity_enroll
               SET enroll_status = 4,
                   sign_out_time = #{signOutTime},
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND user_id = #{userId}
               AND enroll_status = 3
               AND sign_out_time IS NULL
               AND delete_flag = 0
            """)
    int signOutIfSignedIn(@Param("enrollId") Long enrollId,
                          @Param("userId") String userId,
                          @Param("signOutTime") LocalDateTime signOutTime);

    /**
     * 发布者/管理员补签到：由服务层先校验活动管理权限，这里只做状态条件更新。
     *
     * @param enrollId   报名主键
     * @param signInTime 签到时间
     * @return 影响行数；1 表示成功，0 表示不是"已报名"状态
     */
    @Update("""
            UPDATE activity_enroll
               SET enroll_status = 3,
                   sign_in_time = #{signInTime},
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND enroll_status = 1
               AND sign_in_time IS NULL
               AND delete_flag = 0
            """)
    int markSignIn(@Param("enrollId") Long enrollId,
                   @Param("signInTime") LocalDateTime signInTime);

    /**
     * 发布者/管理员补签退：由服务层先校验活动管理权限，这里只做状态条件更新。
     *
     * @param enrollId    报名主键
     * @param signOutTime 签退时间
     * @return 影响行数；1 表示成功，0 表示不是"已签到"状态
     */
    @Update("""
            UPDATE activity_enroll
               SET enroll_status = 4,
                   sign_out_time = #{signOutTime},
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND enroll_status = 3
               AND sign_out_time IS NULL
               AND delete_flag = 0
            """)
    int markSignOut(@Param("enrollId") Long enrollId,
                    @Param("signOutTime") LocalDateTime signOutTime);

    /**
     * 发布者/管理员撤销签到：仅"已签到"可撤销，回到"已报名"。
     *
     * @param enrollId 报名主键
     * @return 影响行数；1 表示成功，0 表示当前不是"已签到"状态
     */
    @Update("""
            UPDATE activity_enroll
               SET enroll_status = 1,
                   sign_in_time = NULL,
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND enroll_status = 3
               AND delete_flag = 0
            """)
    int revertSignIn(@Param("enrollId") Long enrollId);

    /**
     * 发布者/管理员撤销签退：仅"已完成"可撤销，回到"已签到"并清空签退时间。
     *
     * @param enrollId 报名主键
     * @return 影响行数；1 表示成功，0 表示当前不是"已完成"状态
     */
    @Update("""
            UPDATE activity_enroll
               SET enroll_status = 3,
                   sign_out_time = NULL,
                   update_time = NOW()
             WHERE enroll_id = #{enrollId}
               AND enroll_status = 4
               AND delete_flag = 0
            """)
    int revertSignOut(@Param("enrollId") Long enrollId);
}
