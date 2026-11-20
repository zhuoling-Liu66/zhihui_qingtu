package com.example.zhihuiqingtu.team.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.team.entity.TeamMain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 队伍主表数据访问层。
 */
public interface TeamMainRepository extends BaseMapper<TeamMain> {

    @Update("""
            UPDATE team_main
               SET current_size = current_size + 1,
                   team_status = CASE
                       WHEN max_size > 0 AND current_size + 1 >= max_size THEN 1
                       ELSE 0
                   END,
                   update_time = NOW()
             WHERE team_id = #{teamId}
               AND delete_flag = 0
               AND team_status <> 2
               AND (max_size IS NULL OR max_size <= 0 OR current_size < max_size)
            """)
    int tryOccupyMemberSlot(@Param("teamId") Long teamId);

    @Update("""
            UPDATE team_main
               SET current_size = current_size - 1,
                   team_status = CASE
                       WHEN max_size > 0 AND current_size - 1 >= max_size THEN 1
                       ELSE 0
                   END,
                   update_time = NOW()
             WHERE team_id = #{teamId}
               AND delete_flag = 0
               AND team_status <> 2
               AND current_size > 0
            """)
    int tryReleaseMemberSlot(@Param("teamId") Long teamId);

    @Update("""
            UPDATE team_main
               SET team_status = 2,
                   current_size = 0,
                   update_time = NOW()
             WHERE team_id = #{teamId}
               AND leader_user_id = #{leaderUserId}
               AND delete_flag = 0
               AND team_status <> 2
            """)
    int dismissActiveTeam(@Param("teamId") Long teamId,
                          @Param("leaderUserId") String leaderUserId);

    /**
     * 管理员强制解散：不限定队长，仅要求队伍未解散。
     */
    @Update("""
            UPDATE team_main
               SET team_status = 2,
                   current_size = 0,
                   update_time = NOW()
             WHERE team_id = #{teamId}
               AND delete_flag = 0
               AND team_status <> 2
            """)
    int dismissActiveTeamByAdmin(@Param("teamId") Long teamId);
}
