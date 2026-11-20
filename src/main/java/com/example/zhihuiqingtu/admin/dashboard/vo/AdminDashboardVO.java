package com.example.zhihuiqingtu.admin.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.example.zhihuiqingtu.admin.activity.vo.AdminActivityVO;
import com.example.zhihuiqingtu.admin.user.vo.AdminUserVO;

/**
 * 管理端概览数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardVO {

    private Long userTotal;

    private Long studentCount;

    private Long teacherCount;

    private Long adminCount;

    private Long staffCount;

    private Long activityTotal;

    /** 待审核活动数。 */
    private Long pendingActivityCount;

    private Long publishedActivityCount;

    private Long finishedActivityCount;

    private Long canceledActivityCount;

    private Long pendingCreditCount;

    private List<AdminActivityVO> recentActivities;

    private List<AdminUserVO> recentUsers;
}
