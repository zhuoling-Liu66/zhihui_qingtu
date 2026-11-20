package com.example.zhihuiqingtu.admin.activity.dto;

import lombok.Data;

/**
 * 管理端活动查询条件。
 */
@Data
public class AdminActivityQueryRequest {

    private Integer activityType;

    private Integer reviewStatus;

    private Integer activityStatus;

    private String publisherUserId;

    private String keyword;

    private Integer pageNo = 0;

    private Integer pageSize = 10;
}
