package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.team.CreateTeamRequest;
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
 * 组队模块控制器。
 * <p>
 * 负责队伍创建、申请加入、审核成员、退出队伍和解散队伍等操作。
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    /**
     * 组队业务服务。
     */
    private final TeamService teamService;

    /**
     * 创建队伍。
     * <p>
     * 不再需要请求体里的 {@code leaderUserId}：队长就是当前登录用户，
     * 身份由 Spring Security 从 JWT 解析、服务层通过 {@code CurrentUser} 读取。
     * 少一个可伪造的入参，就少一处"把队伍挂到别人名下"的越权面。
     *
     * @param request 创建队伍请求体（不含身份字段）
     * @return 创建后的队伍详情
     */
    @PostMapping
    public ApiResponse<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        return ApiResponse.success("队伍创建成功", teamService.createTeam(request));
    }

    /**
     * 分页查询队伍列表。
     *
     * @param request 查询条件
     * @return 队伍分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<TeamResponse>> queryTeams(@ModelAttribute TeamQueryRequest request) {
        return ApiResponse.success(teamService.queryTeams(request));
    }

    /**
     * 查询队伍详情。
     *
     * @param teamId 队伍主键
     * @return 队伍详情
     */
    @GetMapping("/{teamId}")
    public ApiResponse<TeamResponse> getTeamDetail(@PathVariable Long teamId) {
        return ApiResponse.success(teamService.getTeamDetail(teamId));
    }

    /**
     * 申请加入队伍。
     * <p>
     * 无请求体：申请人由 Spring Security 从 JWT 解析，
     * 路径已定位目标队伍。原来请求体里的 {@code userId} 是客户端自报的，
     * 可以填别人的学号替他人申请，现已移除。
     *
     * @param teamId 队伍主键
     * @return 最新队伍详情
     */
    @PostMapping("/{teamId}/join")
    public ApiResponse<TeamResponse> applyJoin(@PathVariable Long teamId) {
        return ApiResponse.success("已提交入队申请", teamService.applyJoin(teamId));
    }

    /**
     * 审核队员申请。
     * <p>
     * 不再需要请求体里的 {@code leaderUserId}：审核人就是当前登录用户，
     * "是否队长"的归属校验在服务层用真实身份与队伍记录比较，逻辑不变、依据变可信。
     *
     * @param teamId   队伍主键
     * @param memberId 队员记录主键
     * @param request  审核请求体（只剩 memberStatus）
     * @return 最新队伍详情
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
     * <p>
     * 不再需要请求体里的 {@code operatorUserId}：操作人就是当前登录用户。
     * "本人或队长可退出"的规则保留，但判断依据换成 JWT 证明的身份。
     *
     * @param teamId   队伍主键
     * @param memberId 队员记录主键
     * @param request  退出请求体（已无入参字段，请求体可为 {@code {}}）
     * @return 最新队伍详情
     */
    @PatchMapping("/{teamId}/members/{memberId}/exit")
    public ApiResponse<TeamResponse> exitTeam(
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        return ApiResponse.success("退出队伍成功", teamService.exitTeam(teamId, memberId));
    }

    /**
     * 解散队伍。
     * <p>
     * 无请求体：路径已定位目标队伍，操作人由 Spring Security 从 JWT 解析。
     * 解散是破坏性操作（会逻辑删除全队成员记录），必须由队长本人发起。
     *
     * @param teamId 队伍主键
     * @return 最新队伍详情
     */
    @PatchMapping("/{teamId}/dismiss")
    public ApiResponse<TeamResponse> dismissTeam(@PathVariable Long teamId) {
        return ApiResponse.success("队伍已解散", teamService.dismissTeam(teamId));
    }
}
