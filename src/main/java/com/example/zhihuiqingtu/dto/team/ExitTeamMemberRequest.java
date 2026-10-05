package com.example.zhihuiqingtu.dto.team;

import lombok.Data;

/**
 * 退出队伍请求。
 *
 * <p><b>【安全迁移】这个类现在没有入参字段，是一个空类，这是有意的。</b>
 * <p>
 * 它原来只有一个 {@code operatorUserId} 字段（{@code @NotBlank}），由客户端自报
 * "我是操作人"。那是可伪造的身份声明：填入别人的学号，就能把别的队员踢出队伍
 * （退出逻辑允许"本人或队长"操作，伪造队长学号即可越权移除成员）。
 * 操作人身份的唯一可信来源是 Spring Security 认证后的 JWT（sub claim），
 * 所以该字段被删除，操作人改为在 {@code TeamServiceImpl.exitTeam} 内用
 * {@code CurrentUser.requireUserId()} 获取。
 * <p>
 * <b>为什么保留空类而不是删掉整个类：</b>
 * 保持 {@code exitTeam(Long teamId, Long memberId, ExitTeamMemberRequest request)}
 * 的签名和 HTTP 契约（{@code PATCH /api/teams/{teamId}/members/{memberId}/exit}）稳定，
 * 避免调用方与前端被动跟着改；将来如需要"退出原因"等入参，直接往这里加字段即可。
 */
@Data
public class ExitTeamMemberRequest {

    // 无入参字段：操作人身份一律取自当前登录用户（SecurityContext），不接受客户端自报。
}
