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
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.service.TeamService;
import com.example.zhihuiqingtu.vo.team.TeamMemberVO;
import com.example.zhihuiqingtu.vo.team.TeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组队服务实现。
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamMainRepository teamMainRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserInfoRepository userInfoRepository;
    private final ActivityMainRepository activityMainRepository;
    /**
     * 当前登录用户读取入口（基于 Spring Security 的 SecurityContextHolder）。
     * <p>
     * 用它替代原来从请求体读 {@code leaderUserId} / {@code operatorUserId} / {@code userId} ——
     * 那些字段是客户端自报的，任何人都能填别人的学号来冒充队长、冒充操作人，
     * 从而建队挂到别人名下、审批他人申请、甚至解散别人的队伍。
     */
    private final CurrentUser currentUser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse createTeam(CreateTeamRequest request) {
        // ★ 队长 = 当前登录用户（原来读 request.getLeaderUserId()，客户端自报、可伪造）
        // 唯一可信来源是 Spring Security 认证后的 JWT（sub claim），这里取一次复用，避免重复读上下文。
        String leaderUserId = currentUser.requireUserId();
        // 先确认该学号在 user_info 里真实存在，避免 JWT 里的身份指向一个已删除的用户。
        requireUser(leaderUserId);
        if (request.getActivityId() != null) {
            requireActivity(request.getActivityId());
        }
        if (request.getMaxSize() < 0) {
            throw new BusinessException(4301, "最大人数不能小于0");
        }

        int currentSize = 1;
        TeamMain teamMain = TeamMain.builder()
                .activityId(request.getActivityId())
                .leaderUserId(leaderUserId)
                .teamName(request.getTeamName())
                .teamDesc(request.getTeamDesc())
                .maxSize(request.getMaxSize())
                .currentSize(currentSize)
                .teamStatus(isFull(request.getMaxSize(), currentSize)
                        ? TeamStatusEnum.FULL.getCode()
                        : TeamStatusEnum.RECRUITING.getCode())
                .build();
        teamMainRepository.insert(teamMain);

        TeamMember leader = TeamMember.builder()
                .teamId(teamMain.getTeamId())
                .userId(leaderUserId)
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
                .eq(StringUtils.hasText(request.getLeaderUserId()), TeamMain::getLeaderUserId, request.getLeaderUserId())
                .orderByDesc(TeamMain::getCreateTime);

        if (StringUtils.hasText(request.getMemberUserId())) {
            List<TeamMember> memberships = teamMemberRepository.selectList(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getUserId, request.getMemberUserId())
                    .in(TeamMember::getMemberStatus,
                            TeamMemberStatusEnum.PENDING.getCode(),
                            TeamMemberStatusEnum.JOINED.getCode()));
            if (memberships.isEmpty()) {
                Page<TeamResponse> emptyPage = new Page<>(request.getPageNo() + 1L, request.getPageSize(), 0);
                emptyPage.setRecords(Collections.emptyList());
                return PageResponse.from(emptyPage);
            }
            Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
            queryWrapper.in(TeamMain::getTeamId, teamIds);
        }

        Page<TeamMain> page = teamMainRepository.selectPage(
                new Page<>(request.getPageNo() + 1L, request.getPageSize()),
                queryWrapper
        );
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
        // ★ 申请人 = 当前登录用户（原来读 request.getUserId()，客户端自报、可伪造）
        // 否则填别人的学号就能替别人提交入队申请，冒充他人并污染队伍成员列表。
        String applicantId = currentUser.requireUserId();
        requireUser(applicantId);
        if (TeamStatusEnum.DISMISSED.getCode().equals(teamMain.getTeamStatus())) {
            throw new BusinessException(4302, "队伍已解散，无法加入");
        }
        if (isFull(teamMain.getMaxSize(), teamMain.getCurrentSize())) {
            throw new BusinessException(4308, "队伍人数已满");
        }
        if (teamMemberRepository.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, applicantId)) > 0) {
            throw new BusinessException(4303, "当前用户已在队伍中或申请待审核");
        }
        TeamMember teamMember = TeamMember.builder()
                .teamId(teamId)
                .userId(applicantId)
                .memberStatus(TeamMemberStatusEnum.PENDING.getCode())
                .build();
        teamMemberRepository.insert(teamMember);
        return toTeamResponse(teamMain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamResponse reviewMember(Long teamId, Long memberId, ReviewTeamMemberRequest request) {
        TeamMain teamMain = requireTeam(teamId);
        // ★ 审核人 = 当前登录用户（原来读 request.getLeaderUserId()，客户端自报、可伪造）
        // 归属/权限校验逻辑本身不变，只是把"请求体说的队长"换成"JWT 证明的当前用户"：
        // 原来任何人填上队长的学号就能审批队员申请。
        if (!teamMain.getLeaderUserId().equals(currentUser.requireUserId())) {
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
        // ★ 操作人 = 当前登录用户（原来读 request.getOperatorUserId()，客户端自报、可伪造）
        // "本人或队长可以退出"这条规则保留，但判断依据换成 JWT 证明的身份：
        // 原来只要填上队长的学号，任何登录用户都能把别的队员移出队伍。
        String operatorUserId = currentUser.requireUserId();
        if (!operatorUserId.equals(teamMember.getUserId())
                && !operatorUserId.equals(teamMain.getLeaderUserId())) {
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
        // ★ 操作人 = 当前登录用户（原来读 request.getOperatorUserId()，客户端自报、可伪造）
        // 解散会把该队全部成员记录逻辑删除，是可破坏性操作：原来填上队长学号即可解散别人的队伍。
        if (!teamMain.getLeaderUserId().equals(currentUser.requireUserId())) {
            throw new BusinessException(4313, "仅队长可以解散队伍");
        }
        teamMain.setTeamStatus(TeamStatusEnum.DISMISSED.getCode());
        teamMain.setCurrentSize(0);
        teamMainRepository.updateById(teamMain);
        teamMemberRepository.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));
        return toTeamResponse(teamMain);
    }

    private boolean isFull(Integer maxSize, Integer currentSize) {
        return maxSize != null && maxSize > 0 && currentSize >= maxSize;
    }

    private TeamResponse toTeamResponse(TeamMain teamMain) {
        ActivityMain activityMain = teamMain.getActivityId() == null ? null : activityMainRepository.selectById(teamMain.getActivityId());
        List<TeamMemberVO> members = teamMemberRepository.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamMain.getTeamId())
                        .orderByAsc(TeamMember::getMemberStatus)
                        .orderByDesc(TeamMember::getJoinTime))
                .stream()
                .map(member -> TeamMemberVO.builder()
                        .memberId(member.getMemberId())
                        .userId(member.getUserId())
                        .memberStatus(member.getMemberStatus())
                        .joinTime(member.getJoinTime())
                        .build())
                .toList();

        return TeamResponse.builder()
                .teamId(teamMain.getTeamId())
                .activityId(teamMain.getActivityId())
                .activityTitle(activityMain == null ? null : activityMain.getActivityTitle())
                .leaderUserId(teamMain.getLeaderUserId())
                .teamName(teamMain.getTeamName())
                .teamDesc(teamMain.getTeamDesc())
                .maxSize(teamMain.getMaxSize())
                .currentSize(teamMain.getCurrentSize())
                .teamStatus(teamMain.getTeamStatus())
                .createTime(teamMain.getCreateTime())
                .members(members)
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
