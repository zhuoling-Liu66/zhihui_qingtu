package com.example.zhihuiqingtu.dto.recommendation;

import com.example.zhihuiqingtu.validation.CreditUnit;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐查询请求。
 */
@Data
public class RecommendationQueryRequest {

    /**
     * 用户学号或工号。
     */
    @NotBlank(message = "用户不能为空")
    private String userId;

    /**
     * 年级。
     */
    private String grade;

    /**
     * 专业。
     */
    private String major;

    /**
     * 兴趣标签ID列表。
     */
    private List<Long> interestTagIds;

    /**
     * 学分缺口。
     */
    @CreditUnit(message = "学分缺口必须以0.05为单位")
    private BigDecimal creditGap;

    /**
     * 空闲时间片。
     */
    private List<String> availableTimeSlots;

    /**
     * 返回数量。
     */
    private Integer limit = 10;
}
