package com.example.zhihuiqingtu.vo.credit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学分记录返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRecordResponse {

    /**
     * 学分记录ID。
     */
    private Long recordId;

    /**
     * 用户学号或工号。
     */
    private String userId;

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 学分值。
     */
    private BigDecimal credit;

    /**
     * 学分类型。
     */
    private Integer creditType;

    /**
     * 审核状态。
     */
    private Integer verifyStatus;

    /**
     * 驳回原因。
     */
    private String rejectReason;

    /**
     * 审核时间。
     */
    private LocalDateTime verifyTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
