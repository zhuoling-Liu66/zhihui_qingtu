package com.example.zhihuiqingtu.admin.enrollment.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端报名列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminEnrollmentVO {

    private Long enrollId;

    private String userId;

    private String userName;

    private Long activityId;

    private String activityTitle;

    private Integer enrollStatus;

    private LocalDateTime signInTime;

    private Integer feedbackScore;

    private String feedbackContent;

    private LocalDateTime createTime;
}
