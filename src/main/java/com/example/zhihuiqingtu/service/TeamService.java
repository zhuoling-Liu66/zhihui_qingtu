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
 * 队伍服务接口。
 */
public interface TeamService {

    TeamResponse createTeam(CreateTeamRequest request);

    PageResponse<TeamResponse> queryTeams(TeamQueryRequest request);

    TeamResponse getTeamDetail(Long teamId);

    TeamResponse applyJoin(Long teamId, JoinTeamRequest request);

    TeamResponse reviewMember(Long teamId, Long memberId, ReviewTeamMemberRequest request);

    TeamResponse exitTeam(Long teamId, Long memberId, ExitTeamMemberRequest request);

    TeamResponse dismissTeam(Long teamId, DismissTeamRequest request);
}
