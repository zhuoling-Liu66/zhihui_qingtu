package com.example.zhihuiqingtu.dto.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 推荐日志记录请求。
 */
@Data
public class RecommendationLogRequest {

    /**
     * 用户学号或工号。
     */
    @NotBlank(message = "用户不能为空")
    private String userId;

    /**
     * 活动ID。
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    /**
     * 是否点击。
     */
    private Integer isClick = 0;

    /**
     * 是否报名。
     */
    private Integer isEnroll = 0;
}
