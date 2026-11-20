package com.example.zhihuiqingtu.activity.dto;

import com.example.zhihuiqingtu.validation.CreditUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 组合学分的一个档位：该档学分 + 名额。
 */
@Data
public class CreditTierRequest {

    /**
     * 该档学分，必须以 0.05 为单位。
     */
    @NotNull(message = "档位学分不能为空")
    @DecimalMin(value = "0.00", message = "档位学分不能小于0")
    @CreditUnit(message = "档位学分必须以0.05为单位")
    private BigDecimal credit;

    /**
     * 该档名额。
     */
    @NotNull(message = "档位名额不能为空")
    @Min(value = 1, message = "档位名额必须大于0")
    private Integer quota;
}
