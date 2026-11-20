package com.example.zhihuiqingtu.admin.team.controller;

import com.example.zhihuiqingtu.admin.team.dto.AdminTeamQueryRequest;
import com.example.zhihuiqingtu.admin.team.service.AdminTeamService;
import com.example.zhihuiqingtu.admin.team.vo.AdminTeamVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台队伍管理接口。
 */
@RestController
@RequestMapping("/api/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final AdminTeamService adminTeamService;

    /**
     * 分页查询全部队伍。
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminTeamVO>> queryTeams(@ModelAttribute AdminTeamQueryRequest request) {
        return ApiResponse.success(adminTeamService.queryTeams(request));
    }

    /**
     * 强制解散队伍。
     */
    @PatchMapping("/{teamId}/dismiss")
    public ApiResponse<AdminTeamVO> dismissTeam(@PathVariable Long teamId) {
        return ApiResponse.success("队伍已解散", adminTeamService.dismissTeam(teamId));
    }
}
