package com.example.zhihuiqingtu.award.dto;

import lombok.Data;

/**
 * 学分记录查询请求。
 * <p>
 * <b>安全说明：</b>{@code userId} 是"按学号筛选"的查询条件，它本身不是身份，
 * 但把它当成身份的替代品就是水平越权（IDOR）：任何人都能填别人的学号查别人的学分。
 * 因此服务层按角色分支处理：
 * <ul>
 *   <li>管理员 / 老师：审核队列需要看全部记录，允许用 {@code userId} 按学号筛选学生；</li>
 *   <li>发布过第二课堂活动的用户（包括发布资格后来被收回的）：可以看到“自己获得的学分”
 *       以及“自己发布的活动产生的记录”，{@code userId} 只能在该范围内筛选
 *       （学分记录的 {@code userId} 是"获得学分的学生"，不是发布者，所以发布者必须能看到别人的记录）；</li>
 *   <li>从未发布过活动的普通学生：忽略本字段，强制只查当前登录用户自己的记录。</li>
 * </ul>
 * 结论：字段保留（审核场景需要），但<b>是否生效、能在多大范围内生效，由服务端判定，不由客户端决定</b>。
 */
@Data
public class CreditQueryRequest {

    /**
     * 用户学号或工号（仅管理员/老师、以及自己活动范围内的发布者可当筛选条件，普通学生会被忽略）。
     */
    private String userId;

    /**
     * 审核状态。
     */
    private Integer verifyStatus;

    /**
     * 学分类型。
     */
    private Integer creditType;

    private Long activityId;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
