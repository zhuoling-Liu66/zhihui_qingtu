package com.example.zhihuiqingtu.team.service;

import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.team.dto.CreateTeamRequest;
import com.example.zhihuiqingtu.team.dto.ReviewTeamMemberRequest;
import com.example.zhihuiqingtu.team.dto.TeamQueryRequest;
import com.example.zhihuiqingtu.team.vo.TeamResponse;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 组队业务服务接口。
 *
 * <p><b>身份参数约定（安全迁移后）：</b>本接口的任何方法都<b>不接受</b>
 * {@code leaderUserId} / {@code operatorUserId} / {@code userId} 这类"客户端自报身份"的参数。
 * 队长、申请人、审核人、操作人一律由实现类从 Spring Security 上下文
 * （{@code CurrentUser.requireUserId()}）读取，来源是认证过的 JWT，客户端无法伪造。
 * <p>
 * 唯一例外是 {@link com.example.zhihuiqingtu.team.dto.TeamQueryRequest#getLeaderUserId()}：
 * 它是<b>列表查询的筛选条件</b>（"查某个队长带的队"），不是身份声明，
 * 伪造它最多只能多查几条本来就有权查看的公开队伍，不构成越权，因此保留。
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
     * 申请加入队伍。申请人取当前登录用户，不接受客户端传入的身份。
     *
     * @param teamId 队伍主键
     * @return 最新队伍详情
     */
    TeamResponse applyJoin(Long teamId);

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
     * 队员退出队伍。操作人取当前登录用户，规则为"本人或队长"。
     *
     * @param teamId   队伍主键
     * @param memberId 队员记录主键
     * @return 最新队伍详情
     */
    TeamResponse exitTeam(Long teamId, Long memberId);

    /**
     * 解散队伍。操作人取当前登录用户，仅队长可解散。
     *
     * @param teamId 队伍主键
     * @return 最新队伍详情
     */
    TeamResponse dismissTeam(Long teamId);
}
