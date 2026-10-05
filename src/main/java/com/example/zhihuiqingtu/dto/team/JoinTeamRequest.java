package com.example.zhihuiqingtu.dto.team;

import lombok.Data;

/**
 * 申请加入队伍请求。
 *
 * <p><b>【安全迁移】这个类现在没有入参字段，是一个空类，这是有意的。</b>
 * <p>
 * 它原来只有一个 {@code userId} 字段（{@code @NotBlank}），由客户端在请求体里自报
 * "我要以谁的身份申请入队"。那是可伪造的身份声明：只要填别人的学号，
 * 就能替别人提交入队申请（冒充他人、污染队伍成员列表）。
 * 申请人身份的唯一可信来源是 Spring Security 认证后的 JWT（sub claim），
 * 所以该字段被删除，申请人改为在 {@code TeamServiceImpl.applyJoin} 内用
 * {@code CurrentUser.requireUserId()} 获取。
 * <p>
 * <b>为什么保留空类而不是删掉整个类：</b>
 * 接口签名 {@code applyJoin(Long teamId, JoinTeamRequest request)} 和 HTTP 契约
 * （{@code POST /api/teams/{teamId}/join}）保持不变，Controller 不用改、
 * 前端不用改、将来要加入队留言/分队等新入参时也只需往这个类里加字段。
 * 直接删类会让调用方和前端一起改，收益为零、风险不小。
 * <p>
 * 注意：加上 Lombok 的 {@code @Data} 后，这个类仍然可以正常被 Jackson 反序列化
 * （请求体 {@code {}} 或者带多余字段的旧请求体都能接受，Spring Boot 默认忽略未知字段），
 * 只是不再从中读取任何身份信息。
 */
@Data
public class JoinTeamRequest {

    // 无入参字段：申请人身份一律取自当前登录用户（SecurityContext），不接受客户端自报。
}
