package com.example.zhihuiqingtu.enrollment.dto;

import lombok.Data;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 报名记录查询请求。
 * <p>
 * 注意：<b>没有"用户学号"查询条件</b>。这里的 {@code userId} 虽然语义上是过滤条件而不是身份，
 * 但由客户端指定就等于"可以查询任意用户的报名记录"（含他人活动参与情况），
 * 是一个典型的越权读取面。所以改为服务端强制用当前登录用户过滤
 * （见 {@code CurrentUser}），用户只能查到自己的报名记录。
 */
@Data
public class EnrollmentQueryRequest {

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 报名状态。
     */
    private Integer enrollStatus;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
