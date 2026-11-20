package com.example.zhihuiqingtu.admin.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.team.dto.AdminTeamQueryRequest;
import com.example.zhihuiqingtu.admin.support.AdminPages;
import com.example.zhihuiqingtu.admin.team.vo.AdminTeamVO;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.activity.entity.ActivityMain;
import com.example.zhihuiqingtu.team.entity.TeamMain;
import com.example.zhihuiqingtu.team.entity.TeamMember;
import com.example.zhihuiqingtu.user.entity.UserInfo;
import com.example.zhihuiqingtu.team.enums.TeamStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端队伍服务。
 */
@Service
@RequiredArgsConstructor
public class AdminTeamService {

    private final AdminGuard adminGuard;
    private final TeamMainRepository teamMainRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ActivityMainRepository activityMainRepository;
    private final UserInfoRepository userInfoRepository;

    /**
     * 分页查询全部队伍。
     */
    public PageResponse<AdminTeamVO> queryTeams(AdminTeamQueryRequest request) {
        adminGuard.requireAdmin();

        LambdaQueryWrapper<TeamMain> queryWrapper = new LambdaQueryWrapper<TeamMain>()
                .eq(request.getActivityId() != null, TeamMain::getActivityId, request.getActivityId())
                .eq(request.getTeamStatus() != null, TeamMain::getTeamStatus, request.getTeamStatus());
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(TeamMain::getTeamName, keyword)
                    .or().like(TeamMain::getLeaderUserId, keyword));
        }
        queryWrapper.orderByDesc(TeamMain::getCreateTime);

        Page<TeamMain> page = teamMainRepository.selectPage(
                AdminPages.of(request.getPageNo(), request.getPageSize()),
                queryWrapper
        );

        Map<Long, String> activityTitles = loadActivityTitles(
                page.getRecords().stream().map(TeamMain::getActivityId).collect(Collectors.toSet()));
        Map<String, String> leaderNames = loadUserNames(
                page.getRecords().stream().map(TeamMain::getLeaderUserId).collect(Collectors.toSet()));

        Page<AdminTeamVO> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream()
                .map(team -> toVO(team,
                        activityTitles.get(team.getActivityId()),
                        leaderNames.get(team.getLeaderUserId())))
                .toList());
        return PageResponse.from(responsePage);
    }

    /**
     * 管理员强制解散队伍：不要求当前登录用户是队长。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminTeamVO dismissTeam(Long teamId) {
        adminGuard.requireAdmin();
        TeamMain teamMain = requireTeam(teamId);
        if (teamMainRepository.dismissActiveTeamByAdmin(teamId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "队伍已解散或状态已变化，请刷新后重试");
        }
        teamMemberRepository.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));
        teamMain.setTeamStatus(TeamStatusEnum.DISMISSED.getCode());
        teamMain.setCurrentSize(0);
        return toVO(teamMain, loadActivityTitle(teamMain.getActivityId()),
                loadUserName(teamMain.getLeaderUserId()));
    }

    private TeamMain requireTeam(Long teamId) {
        TeamMain teamMain = teamMainRepository.selectById(teamId);
        if (teamMain == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "队伍不存在");
        }
        return teamMain;
    }

    private Map<String, String> loadUserNames(Collection<String> userIds) {
        Set<String> ids = userIds.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getStudentNo, UserInfo::getUserName, (a, b) -> a));
    }

    private Map<Long, String> loadActivityTitles(Collection<Long> activityIds) {
        Set<Long> ids = activityIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return activityMainRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ActivityMain::getActivityId, ActivityMain::getActivityTitle, (a, b) -> a));
    }

    private String loadActivityTitle(Long activityId) {
        if (activityId == null) {
            return null;
        }
        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        return activityMain == null ? null : activityMain.getActivityTitle();
    }

    private String loadUserName(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        UserInfo userInfo = userInfoRepository.selectById(userId);
        return userInfo == null ? null : userInfo.getUserName();
    }

    private AdminTeamVO toVO(TeamMain teamMain, String activityTitle, String leaderName) {
        return AdminTeamVO.builder()
                .teamId(teamMain.getTeamId())
                .teamName(teamMain.getTeamName())
                .teamDesc(teamMain.getTeamDesc())
                .activityId(teamMain.getActivityId())
                .activityTitle(activityTitle)
                .leaderUserId(teamMain.getLeaderUserId())
                .leaderName(leaderName)
                .currentSize(teamMain.getCurrentSize())
                .maxSize(teamMain.getMaxSize())
                .teamStatus(teamMain.getTeamStatus())
                .createTime(teamMain.getCreateTime())
                .build();
    }
}
