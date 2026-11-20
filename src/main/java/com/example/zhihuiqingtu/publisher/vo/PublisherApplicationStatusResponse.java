package com.example.zhihuiqingtu.publisher.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户的发布权限状态与最近一次申请。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublisherApplicationStatusResponse {

    /** 当前发布权限：0 未开通 / 1 已开通。 */
    private Integer publisherStatus;

    /** 当前是否允许提交新申请。 */
    private Boolean canApply;

    /** 是否存在待审批申请。 */
    private Boolean pending;

    /** 最近一次学生申请；从未申请时为 null。 */
    private PublisherPermissionRecordVO latestApplication;
}
