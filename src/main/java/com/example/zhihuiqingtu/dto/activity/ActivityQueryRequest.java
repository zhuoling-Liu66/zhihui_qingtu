package com.example.zhihuiqingtu.dto.activity;

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
     */
    private String publisherUserId;

    /**
     * 页码。
     */
    private Integer pageNo = 0;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
