package com.example.zhihuiqingtu.publisher.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发布权限申请/变更记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublisherPermissionRecordVO {

    private Long recordId;

    private String studentNo;

    private String studentName;

    /** 1 学生申请 / 2 管理员开通 / 3 管理员收回。 */
    private Integer recordType;

    /** 0 待审批 / 1 已通过 / 2 已驳回 / 3 已关闭。 */
    private Integer recordStatus;

    private String applyReason;

    private String operatorUserId;

    private String operatorName;

    private LocalDateTime operateTime;

    private String rejectReason;

    private LocalDateTime createTime;
}
