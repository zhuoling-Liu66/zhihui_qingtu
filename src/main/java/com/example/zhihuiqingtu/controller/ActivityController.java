package com.example.zhihuiqingtu.controller;

import com.example.zhihuiqingtu.common.ApiResponse;
import com.example.zhihuiqingtu.common.PageResponse;
import com.example.zhihuiqingtu.dto.activity.ActivityQueryRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivitySignInRequest;
import com.example.zhihuiqingtu.dto.activity.ChangeActivityStatusRequest;
import com.example.zhihuiqingtu.dto.activity.CreateActivityRequest;
import com.example.zhihuiqingtu.dto.activity.UpdateActivityRequest;
import com.example.zhihuiqingtu.service.ActivityService;
import com.example.zhihuiqingtu.vo.activity.ActivityCoverUploadResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityResponse;
import com.example.zhihuiqingtu.vo.activity.ActivityTagVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 活动域接口控制器。
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 上传活动封面。
     */
    @PostMapping("/covers")
    public ApiResponse<ActivityCoverUploadResponse> uploadActivityCover(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("活动封面上传成功", activityService.uploadActivityCover(file));
    }

    /**
     * 创建活动。
     */
    @PostMapping
    public ApiResponse<ActivityResponse> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success("活动创建成功", activityService.createActivity(request));
    }

    /**
     * 更新活动。
     */
    @PutMapping("/{activityId}")
    public ApiResponse<ActivityResponse> updateActivity(
            @PathVariable Long activityId,
            @Valid @RequestBody UpdateActivityRequest request) {
        return ApiResponse.success("活动更新成功", activityService.updateActivity(activityId, request));
    }

    /**
     * 修改活动状态。
     */
    @PatchMapping("/{activityId}/status")
    public ApiResponse<ActivityResponse> changeActivityStatus(
            @PathVariable Long activityId,
            @Valid @RequestBody ChangeActivityStatusRequest request) {
        return ApiResponse.success("活动状态更新成功", activityService.changeActivityStatus(activityId, request));
    }

    /**
     * 修改签到开关。
     */
    @PatchMapping("/{activityId}/sign-in-switch")
    public ApiResponse<ActivityResponse> updateSignInSwitch(
            @PathVariable Long activityId,
            @Valid @RequestBody ChangeActivitySignInRequest request) {
        return ApiResponse.success("签到开关更新成功", activityService.updateSignInSwitch(activityId, request));
    }

    /**
     * 删除活动。
     */
    @DeleteMapping("/{activityId}")
    public ApiResponse<Void> deleteActivity(
            @PathVariable Long activityId,
            @RequestParam String operatorUserId) {
        activityService.deleteActivity(activityId, operatorUserId);
        return ApiResponse.success("活动删除成功", null);
    }

    /**
     * 分页查询活动列表。
     */
    @GetMapping
    public ApiResponse<PageResponse<ActivityResponse>> queryActivities(@ModelAttribute ActivityQueryRequest request) {
        return ApiResponse.success(activityService.queryActivities(request));
    }

    /**
     * 查询活动详情。
     */
    @GetMapping("/{activityId}")
    public ApiResponse<ActivityResponse> getActivityDetail(@PathVariable Long activityId) {
        return ApiResponse.success(activityService.getActivityDetail(activityId));
    }

    /**
     * 查询全部可用活动标签。
     */
    @GetMapping("/tags")
    public ApiResponse<List<ActivityTagVO>> listActivityTags() {
        return ApiResponse.success(activityService.listActivityTags());
    }
}
