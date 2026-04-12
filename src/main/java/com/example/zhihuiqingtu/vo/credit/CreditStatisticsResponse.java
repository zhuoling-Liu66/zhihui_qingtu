package com.example.zhihuiqingtu.vo.credit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 学分统计返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditStatisticsResponse {

    /**
     * 用户学号或工号。
     */
    private String userId;

    /**
     * 总学分。
     */
    private BigDecimal totalCredit;

    /**
     * 创新创业学分。
     */
    private BigDecimal innovationEntrepreneurshipCredit;

    /**
     * 智育学分。
     */
    private BigDecimal intellectualEducationCredit;

    /**
     * 德育学分。
     */
    private BigDecimal moralEducationCredit;

    /**
     * 体育学分。
     */
    private BigDecimal physicalEducationCredit;

    /**
     * 美育学分。
     */
    private BigDecimal aestheticEducationCredit;

    /**
     * 劳育学分。
     */
    private BigDecimal laborEducationCredit;
}
