package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.team.CreateTeamRequest;
import com.example.zhihuiqingtu.dto.team.DismissTeamRequest;
import com.example.zhihuiqingtu.dto.team.ExitTeamMemberRequest;
import com.example.zhihuiqingtu.dto.team.JoinTeamRequest;
import com.example.zhihuiqingtu.dto.team.ReviewTeamMemberRequest;
import com.example.zhihuiqingtu.dto.team.TeamQueryRequest;
import com.example.zhihuiqingtu.service.TeamService;
import com.example.zhihuiqingtu.vo.team.TeamResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组队域接口控制器。
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /**
     * 创建队伍。
     */
    @PostMapping
    public ApiResponse<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        return ApiResponse.success("队伍创建成功", teamService.createTeam(request));
    }

    /**
     * 查询队伍列表。
     */
    @GetMapping
    public ApiResponse<PageResponse<TeamResponse>> queryTeams(@ModelAttribute TeamQueryRequest request) {
        return ApiResponse.success(teamService.queryTeams(request));
    }

    /**
     * 查询队伍详情。
     */
    @GetMapping("/{teamId}")
    public ApiResponse<TeamResponse> getTeamDetail(@PathVariable Long teamId) {
        return ApiResponse.success(teamService.getTeamDetail(teamId));
    }

    /**
     * 申请加入队伍。
     */
    @PostMapping("/{teamId}/join")
    public ApiResponse<TeamResponse> applyJoin(
            @PathVariable Long teamId,
            @Valid @RequestBody JoinTeamRequest request) {
        return ApiResponse.success("已提交入队申请", teamService.applyJoin(teamId, request));
    }

    /**
     * 审核队员申请。
     */
    @PatchMapping("/{teamId}/members/{memberId}/review")
    public ApiResponse<TeamResponse> reviewMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @Valid @RequestBody ReviewTeamMemberRequest request) {
        return ApiResponse.success("队员审核完成", teamService.reviewMember(teamId, memberId, request));
    }

    /**
     * 队员退出队伍。
     */
    @PatchMapping("/{teamId}/members/{memberId}/exit")
    public ApiResponse<TeamResponse> exitTeam(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @Valid @RequestBody ExitTeamMemberRequest request) {
        return ApiResponse.success("退出队伍成功", teamService.exitTeam(teamId, memberId, request));
    }

    /**
     * 解散队伍。
     */
    @PatchMapping("/{teamId}/dismiss")
    public ApiResponse<TeamResponse> dismissTeam(
            @PathVariable Long teamId,
            @Valid @RequestBody DismissTeamRequest request) {
        return ApiResponse.success("队伍已解散", teamService.dismissTeam(teamId, request));
    }
}
