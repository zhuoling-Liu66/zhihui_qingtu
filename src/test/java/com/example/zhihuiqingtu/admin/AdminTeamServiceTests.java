package com.example.zhihuiqingtu.admin;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.zhihuiqingtu.common.ErrorCode;
import com.example.zhihuiqingtu.team.entity.TeamMain;
import com.example.zhihuiqingtu.team.entity.TeamMember;
import com.example.zhihuiqingtu.team.enums.TeamStatusEnum;
import com.example.zhihuiqingtu.exception.BusinessException;
import com.example.zhihuiqingtu.activity.repository.ActivityMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMainRepository;
import com.example.zhihuiqingtu.team.repository.TeamMemberRepository;
import com.example.zhihuiqingtu.user.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.zhihuiqingtu.admin.support.AdminGuard;
import com.example.zhihuiqingtu.admin.team.service.AdminTeamService;
import com.example.zhihuiqingtu.admin.team.vo.AdminTeamVO;

class AdminTeamServiceTests {

    private static final Long TEAM_ID = 9L;

    private final AdminGuard adminGuard = mock(AdminGuard.class);
    private final TeamMainRepository teamMainRepository = mock(TeamMainRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final ActivityMainRepository activityMainRepository = mock(ActivityMainRepository.class);
    private final UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
    private final AdminTeamService adminTeamService = new AdminTeamService(
            adminGuard, teamMainRepository, teamMemberRepository,
            activityMainRepository, userInfoRepository);

    @Test
    void adminCanForceDismissTeam() {
        when(adminGuard.requireAdmin()).thenReturn("A0001");
        when(teamMainRepository.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamMainRepository.dismissActiveTeamByAdmin(TEAM_ID)).thenReturn(1);

        AdminTeamVO response = adminTeamService.dismissTeam(TEAM_ID);

        assertEquals(TeamStatusEnum.DISMISSED.getCode(), response.getTeamStatus());
        assertEquals(0, response.getCurrentSize());
        verify(teamMemberRepository).delete(any(Wrapper.class));
    }

    @Test
    void alreadyDismissedTeamReturnsConflict() {
        when(adminGuard.requireAdmin()).thenReturn("A0001");
        when(teamMainRepository.selectById(TEAM_ID)).thenReturn(activeTeam());
        when(teamMainRepository.dismissActiveTeamByAdmin(TEAM_ID)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminTeamService.dismissTeam(TEAM_ID));

        assertEquals(ErrorCode.CONFLICT, exception.getCode());
        verify(teamMemberRepository, never()).delete(any(Wrapper.class));
    }

    @Test
    void missingTeamReturnsNotFound() {
        when(adminGuard.requireAdmin()).thenReturn("A0001");
        when(teamMainRepository.selectById(TEAM_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminTeamService.dismissTeam(TEAM_ID));

        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
    }

    private TeamMain activeTeam() {
        return TeamMain.builder()
                .teamId(TEAM_ID)
                .teamName("测试队")
                .leaderUserId("S0001")
                .currentSize(3)
                .maxSize(5)
                .teamStatus(TeamStatusEnum.RECRUITING.getCode())
                .build();
    }
}
