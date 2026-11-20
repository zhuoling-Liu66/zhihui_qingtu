package com.example.zhihuiqingtu.admin.activity.controller;

import com.example.zhihuiqingtu.admin.activity.dto.AdminActivityQueryRequest;
import com.example.zhihuiqingtu.admin.activity.dto.AdminReviewActivityRequest;
import com.example.zhihuiqingtu.admin.activity.service.AdminActivityService;
import com.example.zhihuiqingtu.admin.activity.vo.AdminActivityVO;
import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.activity.dto.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.activity.dto.CreateActivityRequest;
import com.example.zhihuiqingtu.activity.dto.UpdateActivityRequest;
import com.example.zhihuiqingtu.activity.vo.ActivityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台活动接口。
 *
 * <p>管理员：发布全类型活动（免审核）、查看全部、审核、下架、删除。
 * <p>运营人员：创建/编辑自己的学分活动、查看自己的学分活动审核状态。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminActivityController {

    private final AdminActivityService adminActivityService;

    /**
     * 管理员发布活动（全类型，免审核直接公开）。
     */
    @PostMapping("/activities")
    public ApiResponse<ActivityResponse> createActivity(
            @Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success("活动已发布",
                adminActivityService.createActivityByAdmin(request));
    }

    /**
     * 管理员分页查询全部活动。
     */
    @GetMapping("/activities")
    public ApiResponse<PageResponse<AdminActivityVO>> queryActivities(
            @ModelAttribute AdminActivityQueryRequest request) {
        return ApiResponse.success(adminActivityService.queryActivities(request));
    }

    /**
     * 管理员审核活动（通过 / 驳回）。
     */
    @PatchMapping("/activities/{activityId}/review")
    public ApiResponse<AdminActivityVO> reviewActivity(
            @PathVariable Long activityId,
            @Valid @RequestBody AdminReviewActivityRequest request) {
        return ApiResponse.success("活动审核完成",
                adminActivityService.reviewActivity(activityId, request));
    }

    /**
     * 管理员下架或重新发布活动。
     */
    @PatchMapping("/activities/{activityId}/status")
    public ApiResponse<ActivityResponse> changeStatus(
            @PathVariable Long activityId,
            @Valid @RequestBody ChangeActivityStatusRequest request) {
        return ApiResponse.success("活动状态更新成功",
                adminActivityService.changeStatus(activityId, request));
    }

    /**
     * 管理员删除活动及其关联数据。
     */
    @DeleteMapping("/activities/{activityId}")
    public ApiResponse<Void> deleteActivity(@PathVariable Long activityId) {
        adminActivityService.deleteActivity(activityId);
        return ApiResponse.success("活动已删除", null);
    }

    /**
     * 运营人员查询自己发布的活动（学分与志愿）。
     */
    @GetMapping("/credit-activities/mine")
    public ApiResponse<PageResponse<AdminActivityVO>> myActivities(
            @ModelAttribute AdminActivityQueryRequest request) {
        return ApiResponse.success(adminActivityService.queryMyActivities(request));
    }

    /**
     * 运营人员创建学分/志愿活动（提交审核）。
     */
    @PostMapping("/credit-activities")
    public ApiResponse<ActivityResponse> createStaffActivity(
            @Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success("活动已提交审核",
                adminActivityService.createStaffActivity(request));
    }

    /**
     * 运营人员编辑自己的活动（重新提交审核）。
     */
    @PutMapping("/credit-activities/{activityId}")
    public ApiResponse<ActivityResponse> updateStaffActivity(
            @PathVariable Long activityId,
            @Valid @RequestBody UpdateActivityRequest request) {
        return ApiResponse.success("活动已更新并重新提交审核",
                adminActivityService.updateStaffActivity(activityId, request));
    }
}
