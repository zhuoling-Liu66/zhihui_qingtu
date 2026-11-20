package com.example.zhihuiqingtu.activity.dto;

import com.example.zhihuiqingtu.validation.CreditUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * 创建活动请求。
 * <p>
 * 注意：没有"发布者"字段。发布者由服务端从 JWT 取当前登录用户
 * （见 {@code CurrentUser}），因为身份一旦可以由客户端声明，就等于允许任意越权。
 */
@Data
public class CreateActivityRequest {

    /**
     * 活动标题。
     */
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 255, message = "活动标题长度不能超过255")
    private String activityTitle;

    /**
     * 活动描述。
     */
    private String activityDesc;

    /**
     * 活动类型。
     */
    @NotNull(message = "活动类型不能为空")
    private Integer activityType;

    /**
     * 活动学分，仅第二课堂学分活动传入。
     */
    @DecimalMin(value = "0.00", message = "活动学分不能小于0")
    @CreditUnit(message = "活动学分必须以0.05为单位")
    private BigDecimal activityCredit;

    /**
     * 学分类型，仅第二课堂学分活动传入。
     */
    private Integer creditType;

    /**
     * 学分模式：1 固定学分 / 2 组合学分（按档位与名额分配）。为空按固定处理。
     */
    private Integer creditMode;

    /**
     * 组合学分档位，仅学分模式=2 时使用。
     */
    @Valid
    private List<CreditTierRequest> creditTiers;

    /**
     * 志愿服务时长，仅志愿服务活动传入。
     */
    @DecimalMin(value = "0.00", message = "志愿服务时长不能小于0")
    private BigDecimal volunteerHours;

    /**
     * 志愿时长模式：1 固定时长 / 2 灵活时长（活动结束后逐人录入）。为空按固定处理。
     */
    private Integer volunteerMode;

    /**
     * 活动封面地址。
     */
    @Size(max = 500, message = "活动封面地址长度不能超过500")
    private String coverUrl;

    /**
     * 开始时间。
     */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /**
     * 活动地点。
     */
    @Size(max = 255, message = "活动地点长度不能超过255")
    private String activityLocation;

    /**
     * 最大参与人数。
     */
    @NotNull(message = "最大参与人数不能为空")
    private Integer maxParticipant;

    /**
     * 标签ID列表。
     */
    private List<Long> tagIds;
}
