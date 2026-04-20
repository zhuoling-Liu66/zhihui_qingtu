package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.team.CreateTeamRequest;
import com.example.zhihuiqingtu.dto.team.DismissTeamRequest;
import com.example.zhihuiqingtu.dto.team.ExitTeamMemberRequest;
import com.example.zhihuiqingtu.dto.team.JoinTeamRequest;
import com.example.zhihuiqingtu.dto.team.ReviewTeamMemberRequest;
import com.example.zhihuiqingtu.dto.team.TeamQueryRequest;
import com.example.zhihuiqingtu.vo.team.TeamResponse;

/**
 * 组队业务服务接口。
 */
public interface TeamService {

    /**
     * 创建队伍。
     *
     * @param request 创建队伍请求参数
     * @return 创建后的队伍信息
     */
    TeamResponse createTeam(CreateTeamRequest request);

    /**
     * 分页查询队伍列表。
     *
     * @param request 查询条件
     * @return 队伍分页结果
     */
    PageResponse<TeamResponse> queryTeams(TeamQueryRequest request);

    /**
     * 查询队伍详情。
     *
     * @param teamId 队伍主键
     * @return 队伍详情
     */
    TeamResponse getTeamDetail(Long teamId);

    /**
     * 申请加入队伍。
     *
     * @param teamId  队伍主键
     * @param request 入队申请参数
     * @return 最新队伍详情
     */
    TeamResponse applyJoin(Long teamId, JoinTeamRequest request);

    /**
     * 审核队员申请。
     *
     * @param teamId   队伍主键
     * @param memberId 队员记录主键
     * @param request  审核参数
     * @return 最新队伍详情
     */
    TeamResponse reviewMember(Long teamId, Long memberId, ReviewTeamMemberRequest request);

    /**
     * 队员退出队伍。
     *
     * @param teamId   队伍主键
     * @param memberId 队员记录主键
     * @param request  退出参数
     * @return 最新队伍详情
     */
    TeamResponse exitTeam(Long teamId, Long memberId, ExitTeamMemberRequest request);

    /**
     * 解散队伍。
     *
     * @param teamId  队伍主键
     * @param request 解散参数
     * @return 最新队伍详情
     */
    TeamResponse dismissTeam(Long teamId, DismissTeamRequest request);
}
