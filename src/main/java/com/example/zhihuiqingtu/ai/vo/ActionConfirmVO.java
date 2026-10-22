package com.example.zhihuiqingtu.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 待确认动作的执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionConfirmVO {

    private String actionId;

    /** EXECUTED / CANCELLED / EXPIRED / FAILED。 */
    private String status;

    private LocalDateTime executedAt;

    /** 执行结果明细（例如报名记录ID、活动标题、当前人数）。 */
    private Map<String, Object> result;
}
