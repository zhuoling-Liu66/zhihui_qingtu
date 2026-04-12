package com.example.zhihuiqingtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.team.CreateTeamRequest;
import com.example.zhihuiqingtu.dto.team.DismissTeamRequest;
import com.example.zhihuiqingtu.dto.team.ExitTeamMemberRequest;
import com.example.zhihuiqingtu.dto.team.JoinTeamRequest;
import com.example.zhihuiqingtu.dto.team.ReviewTeamMemberRequest;
import com.example.zhihuiqingtu.dto.team.TeamQueryRequest;
import com.example.zhihuiqingtu.entity.ActivityMain;
import com.example.zhihuiqingtu.entity.TeamMain;
import com.example.zhihuiqingtu.entity.TeamMember;
import com.example.zhihuiqingtu.entity.UserInfo;
import com.example.zhihuiqingtu.enums.TeamMemberStatusEnum;
import com.example.zhihuiqingtu.enums.TeamStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.repository.TeamMainRepository;
import com.example.zhihuiqingtu.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.repository.UserInfoRepository;
import com.example.zhihuiqingtu.service.TeamService;
import com.example.zhihuiqingtu.vo.team.TeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 队伍服务实现。
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamMainRepository teamMainRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserInfoRepository userInfoRepository;
    private final ActivityMainRepository activityMainRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse createTeam(CreateTeamRequest request) {
        requireUser(request.getLeaderUserId());
        if (request.getActivityId() != null) {
            requireActivity(request.getActivityId());
        }
        if (request.getMaxSize() < 0) {
            throw new BusinessException(4301, "最大人数不能小于0");
        }
        int currentSize = 1;
        TeamMain teamMain = TeamMain.builder()
                .activityId(request.getActivityId())
                .leaderUserId(request.getLeaderUserId())
                .teamName(request.getTeamName())
                .teamDesc(request.getTeamDesc())
                .maxSize(request.getMaxSize())
                .currentSize(currentSize)
                .teamStatus(isFull(request.getMaxSize(), currentSize) ? TeamStatusEnum.FULL.getCode() : TeamStatusEnum.RECRUITING.getCode())
                .build();
        teamMainRepository.insert(teamMain);

        TeamMember leader = TeamMember.builder()
                .teamId(teamMain.getTeamId())
                .userId(request.getLeaderUserId())
                .memberStatus(TeamMemberStatusEnum.JOINED.getCode())
                .joinTime(LocalDateTime.now())
                .build();
        teamMemberRepository.insert(leader);
        return toTeamResponse(teamMain);
    }

    @Override
    public PageResponse<TeamResponse> queryTeams(TeamQueryRequest request) {
        LambdaQueryWrapper<TeamMain> queryWrapper = new LambdaQueryWrapper<TeamMain>()
                .eq(request.getActivityId() != null, TeamMain::getActivityId, request.getActivityId())
                .eq(request.getTeamStatus() != null, TeamMain::getTeamStatus, request.getTeamStatus())
                .orderByDesc(TeamMain::getCreateTime);
        Page<TeamMain> page = teamMainRepository.selectPage(new Page<>(request.getPageNo() + 1L, request.getPageSize()), queryWrapper);
        Page<TeamResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toTeamResponse).toList());
        return PageResponse.from(responsePage);
    }

    @Override
    public TeamResponse getTeamDetail(Long teamId) {
        return toTeamResponse(requireTeam(teamId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse applyJoin(Long teamId, JoinTeamRequest request) {
        TeamMain teamMain = requireTeam(teamId);
        requireUser(request.getUserId());
        if (TeamStatusEnum.DISMISSED.getCode().equals(teamMain.getTeamStatus())) {
            throw new BusinessException(4302, "队伍已解散，无法加入");
        }
        if (teamMemberRepository.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, request.getUserId())) > 0) {
            throw new BusinessException(4303, "当前用户已在队伍中或申请待审核");
        }
        TeamMember teamMember = TeamMember.builder()
                .teamId(teamId)
                .userId(request.getUserId())
                .memberStatus(TeamMemberStatusEnum.PENDING.getCode())
                .build();
        teamMemberRepository.insert(teamMember);
        return toTeamResponse(teamMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse reviewMember(Long teamId, Long memberId, ReviewTeamMemberRequest request) {
        TeamMain teamMain = requireTeam(teamId);
        if (!teamMain.getLeaderUserId().equals(request.getLeaderUserId())) {
            throw new BusinessException(4304, "仅队长可审核成员申请");
        }
        TeamMember teamMember = requireTeamMember(memberId);
        if (!teamId.equals(teamMember.getTeamId())) {
            throw new BusinessException(4305, "成员不属于当前队伍");
        }
        if (!TeamMemberStatusEnum.PENDING.getCode().equals(teamMember.getMemberStatus())) {
            throw new BusinessException(4306, "仅待审核成员可以审批");
        }
        if (!TeamMemberStatusEnum.JOINED.getCode().equals(request.getMemberStatus())
                && !TeamMemberStatusEnum.REJECTED.getCode().equals(request.getMemberStatus())) {
            throw new BusinessException(4307, "成员审核状态仅支持加入或驳回");
        }
        if (TeamMemberStatusEnum.JOINED.getCode().equals(request.getMemberStatus())
                && isFull(teamMain.getMaxSize(), teamMain.getCurrentSize())) {
            throw new BusinessException(4308, "队伍人数已满");
        }

        teamMember.setMemberStatus(request.getMemberStatus());
        if (TeamMemberStatusEnum.JOINED.getCode().equals(request.getMemberStatus())) {
            teamMember.setJoinTime(LocalDateTime.now());
            teamMain.setCurrentSize(teamMain.getCurrentSize() + 1);
        }
        teamMain.setTeamStatus(isFull(teamMain.getMaxSize(), teamMain.getCurrentSize())
                ? TeamStatusEnum.FULL.getCode()
                : TeamStatusEnum.RECRUITING.getCode());
        teamMemberRepository.updateById(teamMember);
        teamMainRepository.updateById(teamMain);
        return toTeamResponse(teamMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse exitTeam(Long teamId, Long memberId, ExitTeamMemberRequest request) {
        TeamMain teamMain = requireTeam(teamId);
        TeamMember teamMember = requireTeamMember(memberId);
        if (!teamMain.getTeamId().equals(teamMember.getTeamId())) {
            throw new BusinessException(4309, "成员不属于当前队伍");
        }
        if (!request.getOperatorUserId().equals(teamMember.getUserId())
                && !request.getOperatorUserId().equals(teamMain.getLeaderUserId())) {
            throw new BusinessException(4310, "仅本人或队长可以执行退出操作");
        }
        if (teamMain.getLeaderUserId().equals(teamMember.getUserId())) {
            throw new BusinessException(4311, "队长不可直接退出队伍，请先解散队伍");
        }
        if (!TeamMemberStatusEnum.JOINED.getCode().equals(teamMember.getMemberStatus())) {
            throw new BusinessException(4312, "仅已加入成员可以退出队伍");
        }
        teamMemberRepository.deleteById(memberId);
        teamMain.setCurrentSize(Math.max(0, teamMain.getCurrentSize() - 1));
        teamMain.setTeamStatus(isFull(teamMain.getMaxSize(), teamMain.getCurrentSize())
                ? TeamStatusEnum.FULL.getCode()
                : TeamStatusEnum.RECRUITING.getCode());
        teamMainRepository.updateById(teamMain);
        return toTeamResponse(teamMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse dismissTeam(Long teamId, DismissTeamRequest request) {
        TeamMain teamMain = requireTeam(teamId);
        if (!teamMain.getLeaderUserId().equals(request.getOperatorUserId())) {
            throw new BusinessException(4313, "仅队长可以解散队伍");
        }
        teamMain.setTeamStatus(TeamStatusEnum.DISMISSED.getCode());
        teamMain.setCurrentSize(0);
        teamMainRepository.updateById(teamMain);

        List<TeamMember> members = teamMemberRepository.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));
        for (TeamMember member : members) {
            teamMemberRepository.deleteById(member.getMemberId());
        }
        return toTeamResponse(teamMain);
    }

    private boolean isFull(Integer maxSize, Integer currentSize) {
        return maxSize != null && maxSize > 0 && currentSize >= maxSize;
    }

    private TeamResponse toTeamResponse(TeamMain teamMain) {
        return TeamResponse.builder()
                .teamId(teamMain.getTeamId())
                .activityId(teamMain.getActivityId())
                .leaderUserId(teamMain.getLeaderUserId())
                .teamName(teamMain.getTeamName())
                .teamDesc(teamMain.getTeamDesc())
                .maxSize(teamMain.getMaxSize())
                .currentSize(teamMain.getCurrentSize())
                .teamStatus(teamMain.getTeamStatus())
                .createTime(teamMain.getCreateTime())
                .build();
    }

    private TeamMain requireTeam(Long teamId) {
        TeamMain teamMain = teamMainRepository.selectById(teamId);
        if (teamMain == null) {
            throw new BusinessException(4300, "队伍不存在");
        }
        return teamMain;
    }

    private TeamMember requireTeamMember(Long memberId) {
        TeamMember teamMember = teamMemberRepository.selectById(memberId);
        if (teamMember == null) {
            throw new BusinessException(4314, "队员记录不存在");
        }
        return teamMember;
    }

    private UserInfo requireUser(String userId) {
        UserInfo userInfo = userInfoRepository.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(4009, "用户不存在");
        }
        return userInfo;
    }

    private ActivityMain requireActivity(Long activityId) {
        ActivityMain activityMain = activityMainRepository.selectById(activityId);
        if (activityMain == null) {
            throw new BusinessException(4100, "活动不存在");
        }
        return activityMain;
    }
}
