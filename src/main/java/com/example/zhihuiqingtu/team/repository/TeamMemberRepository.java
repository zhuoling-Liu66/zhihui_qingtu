package com.example.zhihuiqingtu.team.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.team.entity.TeamMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 队伍成员数据访问层。
 */
public interface TeamMemberRepository extends BaseMapper<TeamMember> {

    @Update("""
            UPDATE team_member
               SET member_status = #{memberStatus},
                   join_time = #{joinTime},
                   update_time = NOW()
             WHERE member_id = #{memberId}
               AND team_id = #{teamId}
               AND member_status = 0
               AND delete_flag = 0
            """)
    int reviewPendingMember(@Param("memberId") Long memberId,
                            @Param("teamId") Long teamId,
                            @Param("memberStatus") Integer memberStatus,
                            @Param("joinTime") LocalDateTime joinTime);

    @Update("""
            UPDATE team_member
               SET member_status = 3,
                   delete_flag = 1,
                   update_time = NOW()
             WHERE member_id = #{memberId}
               AND team_id = #{teamId}
               AND member_status = 1
               AND delete_flag = 0
            """)
    int deleteJoinedMember(@Param("memberId") Long memberId,
                           @Param("teamId") Long teamId);
}
