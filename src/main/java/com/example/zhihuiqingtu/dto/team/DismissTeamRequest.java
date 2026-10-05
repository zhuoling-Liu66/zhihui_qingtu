package com.example.zhihuiqingtu.dto.team;

import lombok.Data;

/**
 * 解散队伍请求。
 *
 * <p><b>【安全迁移】这个类现在没有入参字段，是一个空类，这是有意的。</b>
 * <p>
 * 它原来只有一个 {@code operatorUserId} 字段（{@code @NotBlank}），由客户端自报
 * "我是操作人"。那是可伪造的身份声明：{@code dismissTeam} 会逻辑删除该队全部成员记录，
 * 只要在请求体里填上队长的学号，任何登录用户都能把别人的队伍解散掉。
 * 操作人身份的唯一可信来源是 Spring Security 认证后的 JWT（sub claim），
 * 所以该字段被删除，操作人改为在 {@code TeamServiceImpl.dismissTeam} 内用
 * {@code CurrentUser.requireUserId()} 获取。
 * <p>
 * <b>为什么保留空类而不是删掉整个类：</b>
 * 保持接口签名与 HTTP 契约（{@code PATCH /api/teams/{teamId}/dismiss}）稳定，
 * Controller、Service 接口、前端调用方式都无需改动；这也符合"请求对象承载一次请求的
 * 全部输入"的写法——现在这次请求的输入恰好是零个字段。
 * <p>
 * 说明：Controller 上仍保留 {@code @Valid @RequestBody DismissTeamRequest request}，
 * 空类可以正常反序列化（请求体 {@code {}} 即可；前端若还在发
 * {@code {operatorUserId: ...}} 这种旧请求体也不会报错，Spring Boot 默认忽略未知字段，
 * 只是这个值不再被信任、不再被使用）。
 */
@Data
public class DismissTeamRequest {

    // 无入参字段：操作人身份一律取自当前登录用户（SecurityContext），不接受客户端自报。
}
