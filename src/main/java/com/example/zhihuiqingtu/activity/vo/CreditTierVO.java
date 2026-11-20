package com.example.zhihuiqingtu.activity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 组合学分档位返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditTierVO {

    private Long tierId;

    /**
     * 该档学分。
     */
    private BigDecimal credit;

    /**
     * 该档名额。
     */
    private Integer quota;

    /**
     * 已占用名额（参与人员列表中返回；不含“不发放”的记录）。
     */
    private Integer used;

    /**
     * 本次导入计划占用的名额（仅 AI 导入预览返回）。
     */
    private Integer selected;
}
