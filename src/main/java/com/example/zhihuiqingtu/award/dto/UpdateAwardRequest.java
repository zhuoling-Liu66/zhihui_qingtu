package com.example.zhihuiqingtu.award.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发布者/管理员编辑某个参与者的发放记录。
 *
 * <p>金额字段按活动类型使用：第二课堂活动填 {@code credit}/{@code creditType}，
 * 志愿活动填 {@code volunteerHours}。{@code verifyStatus} 传 1 表示确认发放、
 * 传 2 表示不发放；不传则只调整金额。
 */
@Data
public class UpdateAwardRequest {

    /**
     * 发放学分，仅第二课堂活动使用。
     */
    private BigDecimal credit;

    /**
     * 学分类型，仅第二课堂活动使用。
     */
    private Integer creditType;

    /**
     * 发放志愿时长，仅志愿活动使用。
     */
    private BigDecimal volunteerHours;

    /**
     * 发放结论：1 已发放 / 2 不发放；为空表示仅调整金额。
     */
    private Integer verifyStatus;

    /**
     * 不发放原因。
     */
    @Size(max = 255, message = "不发放原因长度不能超过255")
    private String rejectReason;
}
