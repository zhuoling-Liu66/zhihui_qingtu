package com.example.zhihuiqingtu.service;

import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.team.dto.ReviewTeamMemberRequest;
import com.example.zhihuiqingtu.team.entity.TeamMain;
import com.example.zhihuiqingtu.team.entity.TeamMember;
import com.example.zhihuiqingtu.team.enums.TeamMemberStatusEnum;
import com.example.zhihuiqingtu.team.enums.TeamStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import com.example.zhihuiqingtu.security.CurrentUser;
import com.example.zhihuiqingtu.team.service.impl.TeamServiceImpl;
import com.example.zhihuiqingtu.team.vo.TeamResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 队伍状态与人数并发语义的定向测试。
 * <p>
 * 数据库层通过 {@code tryOccupyMemberSlot} / {@code reviewPendingMember} /
 * {@code tryReleaseMemberSlot} / {@code deleteJoinedMember} / {@code dismissActiveTeam}
 * 的"条件更新 + 影响行数"保证并发下只变化一次；这里用 Mockito 模拟影响行数，
 * 验证服务层对 0 行结果的分支处理与调用顺序。
 */
class TeamServiceImplTests {

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final String LEADER_ID = "L0001";
    private static final String MEMBER_USER_ID = "M0001";

    private final TeamMainRepository teamMainRepository = mock(TeamMainRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final TeamServiceImpl service = new TeamServiceImpl(
            teamMainRepository,
            teamMemberRepository,
            userInfoRepository,
            activityMainRepository,
            currentUser
    );

    @Test
    void approveMemberOccupiesSlotThenUpdatesStatus() {
        TeamMain team = stubTeam(TeamStatusEnum.RECRUITING.getCode(), 1, 3);
        when(teamMainRepository.tryOccupyMemberSlot(TEAM_ID)).thenReturn(1);
        when(teamMemberRepository.reviewPendingMember(eq(MEMBER_ID), eq(TEAM_ID),
                eq(TeamMemberStatusEnum.JOINED.getCode()), any(LocalDateTime.class))).thenReturn(1);

        TeamResponse response = service.reviewMember(TEAM_ID, MEMBER_ID, approveRequest());

        // 返回值来自提交后重新读取的队伍数据，而不是内存中的旧对象
        assertEquals(TEAM_ID, response.getTeamId());
        verify(teamMainRepository).tryOccupyMemberSlot(TEAM_ID);
        verify(teamMemberRepository).reviewPendingMember(eq(MEMBER_ID), eq(TEAM_ID),
                eq(TeamMemberStatusEnum.JOINED.getCode()), any(LocalDateTime.class));
    }

    @Test
    void lastSlotRaceSecondApprovalReturnsConflict() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 3, 3);
        // 模拟只剩最后一个名额时，另一个并发审批先占用了名额（条件更新影响 0 行）
        when(teamMainRepository.tryOccupyMemberSlot(TEAM_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviewMember(TEAM_ID, MEMBER_ID, approveRequest()));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        // 名额没占到时不能再去改成员状态，避免"成员已加入但人数没加"的半成品状态
        verify(teamMemberRepository, never()).reviewPendingMember(any(), any(), any(), any());
    }

    @Test
    void duplicateApprovalReturnsConflict() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 1, 3);
        when(teamMainRepository.tryOccupyMemberSlot(TEAM_ID)).thenReturn(1);
        // 模拟成员已被另一个请求处理（成员条件更新影响 0 行）
        when(teamMemberRepository.reviewPendingMember(any(), any(), any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviewMember(TEAM_ID, MEMBER_ID, approveRequest()));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    @Test
    void rejectMemberDoesNotOccupySlot() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 1, 3);
        when(teamMemberRepository.reviewPendingMember(eq(MEMBER_ID), eq(TEAM_ID),
                eq(TeamMemberStatusEnum.REJECTED.getCode()), any())).thenReturn(1);

        TeamResponse response = service.reviewMember(TEAM_ID, MEMBER_ID, rejectRequest());

        assertEquals(TEAM_ID, response.getTeamId());
        // 驳回不占名额、不改队伍状态
        verify(teamMainRepository, never()).tryOccupyMemberSlot(TEAM_ID);
        verify(teamMainRepository, never()).tryReleaseMemberSlot(TEAM_ID);
    }

    @Test
    void approvingMemberOfDismissedTeamReturnsConflict() {
        stubTeam(TeamStatusEnum.DISMISSED.getCode(), 0, 3);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reviewMember(TEAM_ID, MEMBER_ID, approveRequest()));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        // 已解散队伍不能通过审批被写回招募中
        verify(teamMainRepository, never()).tryOccupyMemberSlot(TEAM_ID);
        verify(teamMemberRepository, never()).reviewPendingMember(any(), any(), any(), any());
    }

    @Test
    void exitTeamReleasesSlotAndDeletesMemberOnce() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 2, 3);
        stubJoinedMember();
        when(teamMainRepository.tryReleaseMemberSlot(TEAM_ID)).thenReturn(1);
        when(teamMemberRepository.deleteJoinedMember(MEMBER_ID, TEAM_ID)).thenReturn(1);

        TeamResponse response = service.exitTeam(TEAM_ID, MEMBER_ID);

        assertEquals(TEAM_ID, response.getTeamId());
        verify(teamMainRepository).tryReleaseMemberSlot(TEAM_ID);
        verify(teamMemberRepository).deleteJoinedMember(MEMBER_ID, TEAM_ID);
    }

    @Test
    void duplicateExitOnlyReleasesOnce() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 2, 3);
        stubJoinedMember();
        // 模拟第二次退出：成员记录条件更新影响 0 行（第一次已把成员标记为退出）
        when(teamMainRepository.tryReleaseMemberSlot(TEAM_ID)).thenReturn(1);
        when(teamMemberRepository.deleteJoinedMember(MEMBER_ID, TEAM_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exitTeam(TEAM_ID, MEMBER_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        // 抛出冲突后事务整体回滚：名额释放也会被回滚，不会出现"人数减了但成员还在"或反向的半成品
    }

    @Test
    void exitFromDismissedTeamReturnsConflict() {
        stubTeam(TeamStatusEnum.DISMISSED.getCode(), 0, 3);
        stubJoinedMember();
        when(teamMainRepository.tryReleaseMemberSlot(TEAM_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exitTeam(TEAM_ID, MEMBER_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(teamMemberRepository, never()).deleteJoinedMember(MEMBER_ID, TEAM_ID);
    }

    @Test
    void dismissTeamUsesConditionalUpdateAndReturnsFreshData() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 3, 3);
        when(teamMainRepository.dismissActiveTeam(TEAM_ID, LEADER_ID)).thenReturn(1);

        TeamResponse response = service.dismissTeam(TEAM_ID);

        assertEquals(TEAM_ID, response.getTeamId());
        verify(teamMainRepository).dismissActiveTeam(TEAM_ID, LEADER_ID);
        // 不再使用无条件 updateById 写回内存中的旧队伍对象
        verify(teamMainRepository, never()).updateById(any(TeamMain.class));
    }

    @Test
    void dismissAlreadyDismissedTeamReturnsConflict() {
        stubTeam(TeamStatusEnum.DISMISSED.getCode(), 0, 3);
        // 模拟并发下另一个解散请求先提交（条件更新影响 0 行）
        when(teamMainRepository.dismissActiveTeam(TEAM_ID, LEADER_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.dismissTeam(TEAM_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
    }

    @Test
    void dismissByNonLeaderReturnsForbidden() {
        stubTeam(TeamStatusEnum.RECRUITING.getCode(), 1, 3);
        when(currentUser.requireUserId()).thenReturn("OUTSIDER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.dismissTeam(TEAM_ID));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(teamMainRepository, never()).dismissActiveTeam(any(), any());
    }

    private TeamMain stubTeam(Integer teamStatus, Integer currentSize, Integer maxSize) {
        when(currentUser.requireUserId()).thenReturn(LEADER_ID);
        TeamMain team = TeamMain.builder()
                .teamId(TEAM_ID)
                .leaderUserId(LEADER_ID)
                .teamName("测试队伍")
                .maxSize(maxSize)
                .currentSize(currentSize)
                .teamStatus(teamStatus)
                .build();
        when(teamMainRepository.selectById(TEAM_ID)).thenReturn(team);
        when(teamMemberRepository.selectById(MEMBER_ID)).thenReturn(TeamMember.builder()
                .memberId(MEMBER_ID)
                .teamId(TEAM_ID)
                .userId(MEMBER_USER_ID)
                .memberStatus(TeamMemberStatusEnum.PENDING.getCode())
                .build());
        when(teamMemberRepository.selectList(any())).thenReturn(java.util.Collections.emptyList());
        return team;
    }

    private void stubJoinedMember() {
        when(teamMemberRepository.selectById(MEMBER_ID)).thenReturn(TeamMember.builder()
                .memberId(MEMBER_ID)
                .teamId(TEAM_ID)
                .userId(MEMBER_USER_ID)
                .memberStatus(TeamMemberStatusEnum.JOINED.getCode())
                .build());
    }

    private ReviewTeamMemberRequest approveRequest() {
        ReviewTeamMemberRequest request = new ReviewTeamMemberRequest();
        request.setMemberStatus(TeamMemberStatusEnum.JOINED.getCode());
        return request;
    }

    private ReviewTeamMemberRequest rejectRequest() {
        ReviewTeamMemberRequest request = new ReviewTeamMemberRequest();
        request.setMemberStatus(TeamMemberStatusEnum.REJECTED.getCode());
        return request;
    }
}
