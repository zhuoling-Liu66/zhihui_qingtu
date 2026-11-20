package com.example.zhihuiqingtu.activity.dto;

import lombok.Data;

/**
 * 活动查询请求。
 */
@Data
public class ActivityQueryRequest {

    /**
     * 关键字。
     */
    private String keyword;

    /**
     * 活动类型。
     */
    private Integer activityType;

    /**
     * 持久化状态或展示状态。
     */
    private Integer activityStatus;

    /**
     * 发布者学号或工号。
     *
     * <p>公开查询会强制只返回审核通过的活动；只有请求的是当前登录用户自己的活动时，
     * 才会返回待审核/已驳回的活动，供「我发布的活动」展示审核进度。
     */
    private String publisherUserId;

    /**
     * 审核状态筛选：0 待审核 / 1 已通过 / 2 已驳回。
     *
     * <p>仅在查询自己的活动（或管理端）时生效；他人查询会被强制收敛为已通过。
     */
    private Integer reviewStatus;

    /**
     * 专题标识（白名单）。
     * <p>
     * 只接受固定的专题标识（second-class / volunteer / innovation / ... / team），
     * 不接受任意查询表达式，避免把"前端筛选规则"变成可以由客户端任意拼接的查询条件。
     * 未知标识会被忽略（等同不按专题过滤）。
     */
    private String section;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
