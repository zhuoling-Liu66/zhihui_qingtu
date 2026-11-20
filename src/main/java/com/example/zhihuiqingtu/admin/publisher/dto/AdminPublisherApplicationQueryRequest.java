package com.example.zhihuiqingtu.admin.publisher.dto;

import lombok.Data;

/**
 * 管理端发布资格申请查询条件。
 */
@Data
public class AdminPublisherApplicationQueryRequest {

    private Integer recordStatus;

    private Integer recordType;

    private String keyword;

    private Integer pageNo = 0;

    private Integer pageSize = 10;
}
